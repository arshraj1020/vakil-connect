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
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
