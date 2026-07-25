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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
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
        String clientEmail = uniqueEmail("client");
        String clientToken = registerAndLoginClient(clientEmail);

        String lawyerEmail = uniqueEmail("lawyer");
        String lawyerToken = registerAndLoginLawyer(lawyerEmail);

        // Verification is a precondition, not the behaviour under test, and a
        // lawyer cannot self-verify (admin-only), so set it directly.
        User lawyerUser = userRepository.findByEmail(lawyerEmail).orElseThrow();
        Lawyer lawyer = lawyerRepository.findByUser(lawyerUser).orElseThrow();
        lawyer.setVerified(true);
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
}
