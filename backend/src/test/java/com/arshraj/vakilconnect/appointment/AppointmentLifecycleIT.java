package com.arshraj.vakilconnect.appointment;

import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.enums.ConsultationMode;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end appointment lifecycle tests.
 *
 * Group 1: happy-path booking.
 */
@DisplayName("Appointment lifecycle")
class AppointmentLifecycleIT extends AbstractIntegrationTest {

    private static final LocalTime SLOT_START = LocalTime.of(10, 0);
    private static final LocalTime SLOT_END = LocalTime.of(13, 0);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    /**
     * Minimum data a booking needs: a CLIENT, and a VERIFIED lawyer who is
     * available on Mondays 10:00-13:00.
     */
    private record Fixture(String clientEmail,
                           String clientToken,
                           String lawyerToken,
                           UUID lawyerId,
                           LocalDate nextMonday) {
    }

    private Fixture seedBookableLawyer() throws Exception {
        return seedLawyer(true);
    }

    /**
     * @param verified whether the lawyer should be admin-verified. Booking is
     *                 only permitted against verified lawyers.
     */
    private Fixture seedLawyer(boolean verified) throws Exception {
        String clientEmail = uniqueEmail("client");
        String clientToken = registerAndLoginClient(clientEmail);

        String lawyerEmail = uniqueEmail("lawyer");
        String lawyerToken = registerAndLoginLawyer(lawyerEmail);

        // Verification is a precondition, not the behaviour under test, and a
        // lawyer cannot self-verify (admin-only), so set it directly.
        User lawyerUser = userRepository.findByEmail(lawyerEmail).orElseThrow();
        Lawyer lawyer = lawyerRepository.findByUser(lawyerUser).orElseThrow();
        lawyer.setVerified(verified);
        lawyerRepository.save(lawyer);

        // Availability is created through the lawyer's own endpoint.
        mockMvc.perform(post("/api/lawyer/availability")
                        .header(HttpHeaders.AUTHORIZATION, bearer(lawyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "dayOfWeek", "MONDAY",
                                "startTime", "10:00",
                                "endTime", "13:00"))))
                .andExpect(status().isCreated());

        // TemporalAdjusters.next always returns a date strictly after today,
        // which also satisfies the @Future constraint on appointmentDate.
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        return new Fixture(clientEmail, clientToken, lawyerToken, lawyer.getId(), nextMonday);
    }

    /** A well-formed booking body; individual tests override fields as needed. */
    private Map<String, Object> bookingRequest(UUID lawyerId, LocalDate date, String time) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lawyerId", lawyerId.toString());
        body.put("appointmentDate", date.toString());
        body.put("appointmentTime", time);
        body.put("consultationMode", "ONLINE");
        body.put("notes", "First consultation");
        return body;
    }

    private ResultActions book(String clientToken, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/client/appointments")
                .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    /** Books a PENDING appointment and returns its id. */
    private UUID bookPending(Fixture fixture, String time) throws Exception {
        MvcResult result = book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), time))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    private ResultActions cancel(String clientToken, UUID appointmentId) throws Exception {
        return mockMvc.perform(put("/api/client/appointments/{id}/cancel", appointmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)));
    }

    /** action is one of: accept, reject, complete. */
    private ResultActions lawyerAction(String lawyerToken, UUID appointmentId, String action)
            throws Exception {
        return mockMvc.perform(put("/api/lawyer/appointments/{id}/{action}", appointmentId, action)
                .header(HttpHeaders.AUTHORIZATION, bearer(lawyerToken)));
    }

    private Appointment reload(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow();
    }

    @Test
    @DisplayName("a client books an available slot with a verified lawyer")
    void clientBooksAvailableSlot() throws Exception {
        Fixture fixture = seedBookableLawyer();
        LocalTime bookingTime = LocalTime.of(10, 30);

        mockMvc.perform(post("/api/client/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "lawyerId", fixture.lawyerId().toString(),
                                "appointmentDate", fixture.nextMonday().toString(),
                                "appointmentTime", "10:30:00",
                                "consultationMode", "ONLINE",
                                "notes", "First consultation"))))
                // --- response DTO ---
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lawyerId").value(fixture.lawyerId().toString()))
                .andExpect(jsonPath("$.lawyerName").value("Test Lawyer"))
                .andExpect(jsonPath("$.clientName").value("Test Client"))
                .andExpect(jsonPath("$.clientId").exists())
                .andExpect(jsonPath("$.appointmentDate").value(fixture.nextMonday().toString()))
                .andExpect(jsonPath("$.appointmentTime").exists())
                .andExpect(jsonPath("$.consultationMode").value("ONLINE"))
                .andExpect(jsonPath("$.notes").value("First consultation"))
                .andExpect(jsonPath("$.createdAt").exists());

        // --- persisted state ---
        User client = userRepository.findByEmail(fixture.clientEmail()).orElseThrow();
        List<Appointment> persisted = appointmentRepository
                .findByClientOrderByAppointmentDateDescAppointmentTimeDesc(client);

        assertThat(persisted).hasSize(1);

        Appointment appointment = persisted.get(0);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(appointment.getLawyer().getId()).isEqualTo(fixture.lawyerId());
        assertThat(appointment.getClient().getId()).isEqualTo(client.getId());
        assertThat(appointment.getAppointmentDate()).isEqualTo(fixture.nextMonday());
        assertThat(appointment.getAppointmentTime()).isEqualTo(bookingTime);
        assertThat(appointment.getConsultationMode()).isEqualTo(ConsultationMode.ONLINE);
        assertThat(appointment.getNotes()).isEqualTo("First consultation");
        assertThat(appointment.getAppointmentTime()).isBetween(SLOT_START, SLOT_END);
    }

    // ------------------------------------------------ booking business rules

    @Test
    @DisplayName("booking an unverified lawyer is rejected")
    void bookingWithUnverifiedLawyerReturns409() throws Exception {
        // Availability is present, so the only rule being violated is verification.
        Fixture fixture = seedLawyer(false);

        book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), "10:30:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("This lawyer is not yet verified and cannot accept appointments."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("booking outside the lawyer's availability window is rejected")
    void bookingOutsideAvailabilityReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();

        // 20:00 is well outside the seeded 10:00-13:00 Monday window.
        book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), "20:00:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("The lawyer is not available at the requested day and time."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("booking a slot that is already taken is rejected")
    void bookingDuplicateSlotReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        Map<String, Object> request =
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), "11:00:00");

        book(fixture.clientToken(), request).andExpect(status().isCreated());

        book(fixture.clientToken(), request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("This appointment slot has already been booked."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        // The first booking survived; the duplicate did not create a second row.
        User client = userRepository.findByEmail(fixture.clientEmail()).orElseThrow();
        assertThat(appointmentRepository.countByClient(client)).isEqualTo(1);
    }

    @Test
    @DisplayName("booking an unknown lawyer returns not found")
    void bookingUnknownLawyerReturns404() throws Exception {
        Fixture fixture = seedBookableLawyer();

        book(fixture.clientToken(),
                bookingRequest(UUID.randomUUID(), fixture.nextMonday(), "10:30:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Lawyer not found"))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("a booking missing required fields fails validation")
    void bookingValidationFailureReturns400() throws Exception {
        Fixture fixture = seedBookableLawyer();

        Map<String, Object> request =
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), "10:30:00");
        request.remove("lawyerId");
        request.remove("consultationMode");

        book(fixture.clientToken(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.lawyerId").exists())
                .andExpect(jsonPath("$.fieldErrors.consultationMode").exists())
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("a booking with an unknown consultation mode is rejected as unreadable")
    void bookingInvalidEnumReturns400() throws Exception {
        Fixture fixture = seedBookableLawyer();

        Map<String, Object> request =
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), "10:30:00");
        request.put("consultationMode", "PHONE");

        book(fixture.clientToken(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    /** A rejected booking must leave no trace. */
    private void assertNothingPersisted(String clientEmail) {
        User client = userRepository.findByEmail(clientEmail).orElseThrow();
        assertThat(appointmentRepository.countByClient(client)).isZero();
    }

    /** Asserts exactly one appointment was persisted for the client, at the given time. */
    private void assertSinglePersistedAt(String clientEmail, LocalTime expectedTime) {
        User client = userRepository.findByEmail(clientEmail).orElseThrow();
        List<Appointment> persisted = appointmentRepository
                .findByClientOrderByAppointmentDateDescAppointmentTimeDesc(client);

        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getAppointmentTime()).isEqualTo(expectedTime);
        assertThat(persisted.get(0).getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    /**
     * Asserts a rejected transition wrote nothing. Comparing updatedAt as well
     * as status proves no @PreUpdate fired, i.e. no write was even attempted —
     * status alone would still pass if a row had been written back unchanged.
     */
    private void assertUnchanged(UUID appointmentId,
                                 AppointmentStatus expectedStatus,
                                 LocalDateTime expectedUpdatedAt) {
        Appointment after = reload(appointmentId);
        assertThat(after.getStatus()).isEqualTo(expectedStatus);
        assertThat(after.getUpdatedAt()).isEqualTo(expectedUpdatedAt);
    }

    // ------------------------------------------------- client cancellation

    @Test
    @DisplayName("a client cancels their own PENDING appointment")
    void clientCancelsPendingAppointmentReturns200() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "10:30:00");

        cancel(fixture.clientToken(), appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lawyerId").value(fixture.lawyerId().toString()));

        assertThat(reload(appointmentId).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a client cancels their own ACCEPTED appointment")
    void clientCancelsAcceptedAppointmentReturns200() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        cancel(fixture.clientToken(), appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reload(appointmentId).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a COMPLETED appointment can no longer be cancelled")
    void clientCannotCancelCompletedAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:30:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept").andExpect(status().isOk());
        lawyerAction(fixture.lawyerToken(), appointmentId, "complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Snapshot before the rejected call so we can prove nothing was written.
        Appointment before = reload(appointmentId);
        LocalDateTime updatedAtBefore = before.getUpdatedAt();

        cancel(fixture.clientToken(), appointmentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("This appointment can no longer be cancelled."))
                .andExpect(jsonPath("$.path")
                        .value("/api/client/appointments/" + appointmentId + "/cancel"));

        Appointment after = reload(appointmentId);
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    @Test
    @DisplayName("a client cannot cancel another client's appointment")
    void clientCannotCancelAnotherClientsAppointmentReturns404() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:00:00");

        String otherClientToken = registerAndLoginClient(uniqueEmail("otherclient"));

        Appointment before = reload(appointmentId);
        LocalDateTime updatedAtBefore = before.getUpdatedAt();

        // Ownership is enforced by querying with the owner as a predicate, so a
        // non-owner gets 404 rather than 403 (does not disclose existence).
        cancel(otherClientToken, appointmentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.path")
                        .value("/api/client/appointments/" + appointmentId + "/cancel"));

        Appointment after = reload(appointmentId);
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }

    // ------------------------------------------- lawyer lifecycle transitions

    @Test
    @DisplayName("a lawyer accepts a PENDING appointment")
    void lawyerAcceptsPendingAppointmentReturns200() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "10:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.lawyerId").value(fixture.lawyerId().toString()));

        assertThat(reload(appointmentId).getStatus()).isEqualTo(AppointmentStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a lawyer rejects a PENDING appointment")
    void lawyerRejectsPendingAppointmentReturns200() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "10:30:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(reload(appointmentId).getStatus()).isEqualTo(AppointmentStatus.REJECTED);
    }

    @Test
    @DisplayName("a lawyer completes an ACCEPTED appointment")
    void lawyerCompletesAcceptedAppointmentReturns200() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept").andExpect(status().isOk());

        lawyerAction(fixture.lawyerToken(), appointmentId, "complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(reload(appointmentId).getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("an already ACCEPTED appointment cannot be accepted again")
    void lawyerCannotAcceptAcceptedAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:30:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept").andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending appointments can be accepted."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/accept"));

        assertUnchanged(appointmentId, AppointmentStatus.ACCEPTED, updatedAtBefore);
    }

    @Test
    @DisplayName("an ACCEPTED appointment cannot be rejected")
    void lawyerCannotRejectAcceptedAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept").andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "reject")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending appointments can be rejected."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/reject"));

        assertUnchanged(appointmentId, AppointmentStatus.ACCEPTED, updatedAtBefore);
    }

    @Test
    @DisplayName("a PENDING appointment cannot be completed before it is accepted")
    void lawyerCannotCompletePendingAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:30:00");

        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "complete")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Only accepted appointments can be marked as completed."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/complete"));

        assertUnchanged(appointmentId, AppointmentStatus.PENDING, updatedAtBefore);
    }

    // ------------------------------------------------ lawyer ownership checks

    @Test
    @DisplayName("a lawyer cannot accept another lawyer's appointment")
    void lawyerCannotAcceptAnotherLawyersAppointmentReturns404() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "10:00:00");

        String otherLawyerToken = registerAndLoginLawyer(uniqueEmail("otherlawyer"));
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(otherLawyerToken, appointmentId, "accept")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/accept"));

        assertUnchanged(appointmentId, AppointmentStatus.PENDING, updatedAtBefore);
    }

    @Test
    @DisplayName("a lawyer cannot reject another lawyer's appointment")
    void lawyerCannotRejectAnotherLawyersAppointmentReturns404() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "10:30:00");

        String otherLawyerToken = registerAndLoginLawyer(uniqueEmail("otherlawyer"));
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(otherLawyerToken, appointmentId, "reject")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/reject"));

        assertUnchanged(appointmentId, AppointmentStatus.PENDING, updatedAtBefore);
    }

    @Test
    @DisplayName("a lawyer cannot complete another lawyer's appointment")
    void lawyerCannotCompleteAnotherLawyersAppointmentReturns404() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept").andExpect(status().isOk());

        String otherLawyerToken = registerAndLoginLawyer(uniqueEmail("otherlawyer"));
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(otherLawyerToken, appointmentId, "complete")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Appointment not found"))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/complete"));

        assertUnchanged(appointmentId, AppointmentStatus.ACCEPTED, updatedAtBefore);
    }

    // --------------------------------------------- transitions from terminal

    @Test
    @DisplayName("a CANCELLED appointment cannot be cancelled again")
    void clientCannotCancelCancelledAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "11:30:00");

        cancel(fixture.clientToken(), appointmentId).andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        cancel(fixture.clientToken(), appointmentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("This appointment can no longer be cancelled."))
                .andExpect(jsonPath("$.path")
                        .value("/api/client/appointments/" + appointmentId + "/cancel"));

        assertUnchanged(appointmentId, AppointmentStatus.CANCELLED, updatedAtBefore);
    }

    @Test
    @DisplayName("a REJECTED appointment cannot be accepted")
    void lawyerCannotAcceptRejectedAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:00:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "reject").andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending appointments can be accepted."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/accept"));

        assertUnchanged(appointmentId, AppointmentStatus.REJECTED, updatedAtBefore);
    }

    @Test
    @DisplayName("a CANCELLED appointment cannot be accepted")
    void lawyerCannotAcceptCancelledAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:30:00");

        cancel(fixture.clientToken(), appointmentId).andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "accept")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending appointments can be accepted."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/accept"));

        assertUnchanged(appointmentId, AppointmentStatus.CANCELLED, updatedAtBefore);
    }

    @Test
    @DisplayName("a REJECTED appointment cannot be completed")
    void lawyerCannotCompleteRejectedAppointmentReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();
        UUID appointmentId = bookPending(fixture, "12:45:00");

        lawyerAction(fixture.lawyerToken(), appointmentId, "reject").andExpect(status().isOk());
        LocalDateTime updatedAtBefore = reload(appointmentId).getUpdatedAt();

        lawyerAction(fixture.lawyerToken(), appointmentId, "complete")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Only accepted appointments can be marked as completed."))
                .andExpect(jsonPath("$.path")
                        .value("/api/lawyer/appointments/" + appointmentId + "/complete"));

        assertUnchanged(appointmentId, AppointmentStatus.REJECTED, updatedAtBefore);
    }

    // ---------------------------------------------------- availability window
    //
    // The seeded window is MONDAY 10:00-13:00 and the service matches with
    //     time >= startTime && time < endTime
    // so the opening time is INCLUSIVE and the closing time is EXCLUSIVE.

    @Test
    @DisplayName("booking exactly at the opening time succeeds (start is inclusive)")
    void bookingAtOpeningTimeSucceeds() throws Exception {
        Fixture fixture = seedBookableLawyer();

        book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), SLOT_START.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.appointmentDate").value(fixture.nextMonday().toString()));

        assertSinglePersistedAt(fixture.clientEmail(), SLOT_START);
    }

    @Test
    @DisplayName("booking one minute before opening is rejected")
    void bookingOneMinuteBeforeOpeningReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();

        book(fixture.clientToken(), bookingRequest(fixture.lawyerId(), fixture.nextMonday(),
                SLOT_START.minusMinutes(1).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("The lawyer is not available at the requested day and time."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("booking one minute before closing succeeds")
    void bookingOneMinuteBeforeClosingSucceeds() throws Exception {
        Fixture fixture = seedBookableLawyer();
        LocalTime lastBookableMinute = SLOT_END.minusMinutes(1);

        book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), lastBookableMinute.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertSinglePersistedAt(fixture.clientEmail(), lastBookableMinute);
    }

    @Test
    @DisplayName("booking exactly at the closing time is rejected (end is exclusive)")
    void bookingAtClosingTimeReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();

        book(fixture.clientToken(),
                bookingRequest(fixture.lawyerId(), fixture.nextMonday(), SLOT_END.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("The lawyer is not available at the requested day and time."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }

    @Test
    @DisplayName("booking one minute after closing is rejected")
    void bookingOneMinuteAfterClosingReturns409() throws Exception {
        Fixture fixture = seedBookableLawyer();

        book(fixture.clientToken(), bookingRequest(fixture.lawyerId(), fixture.nextMonday(),
                SLOT_END.plusMinutes(1).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("The lawyer is not available at the requested day and time."))
                .andExpect(jsonPath("$.path").value("/api/client/appointments"));

        assertNothingPersisted(fixture.clientEmail());
    }
}
