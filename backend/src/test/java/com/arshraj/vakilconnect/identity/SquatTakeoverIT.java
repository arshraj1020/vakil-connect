package com.arshraj.vakilconnect.identity;

import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.enums.ConsultationMode;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.identity.repository.EmailTokenRepository;
import com.arshraj.vakilconnect.identity.service.TokenHasher;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.support.EmailCaptureConfig;
import com.arshraj.vakilconnect.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email-squatting defence (TDD D8/R1).
 *
 * `users.email` is UNIQUE and registration does not require verification, so
 * without takeover an attacker could register somebody else's address, never
 * verify, and block the real owner permanently. These tests pin every condition
 * under which the row may - and may not - be claimed.
 */
@DisplayName("7-day unverified account takeover")
@Import(EmailCaptureConfig.class)
class SquatTakeoverIT extends AbstractIntegrationTest {

    @Autowired
    private EmailCaptureConfig.RecordingEmailSender mailbox;

    @Autowired
    private EmailTokenRepository emailTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clearMailbox() {
        mailbox.reset();
    }

    private String registerClient(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andExpect(status().isCreated());
        return email;
    }

    /**
     * Backdates the account so the takeover window has elapsed.
     *
     * VIA JDBC, NOT THE ENTITY. BaseEntity maps created_at with
     * {@code updatable = false} and exposes no setter - deliberately, since an
     * application that can rewrite its own audit timestamps is a bad idea. A
     * test that needs an old row therefore has to go under JPA, and this is the
     * honest way to do it rather than weakening the mapping.
     *
     * The persistence context is cleared afterwards so the next read sees the
     * new value instead of the cached entity.
     */
    private User age(String email, int days) {
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE email = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(days)), email);

        entityManager.clear();
        return userRepositoryForSupport.findByEmail(email).orElseThrow();
    }

    private int reRegister(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clientRegistration(email))))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------ allowed

    @Test
    @DisplayName("an unverified CLIENT older than 7 days CAN be claimed")
    void takeoverAllowedPastThreshold() throws Exception {
        String email = registerClient("squatold");
        User before = age(email, 8);
        String oldPasswordHash = before.getPasswordHash();
        mailbox.reset();

        assertEquals(201, reRegister(email));

        User after = userRepositoryForSupport.findByEmail(email).orElseThrow();

        // Same row - claimed, not duplicated. A new row is impossible anyway
        // (uq_users_email), so this proves it was an update.
        assertEquals(before.getId(), after.getId());
        assertFalse(after.getPasswordHash().equals(oldPasswordHash),
                "the claimant's password must replace the squatter's");
        assertFalse(after.isEmailVerified());
        assertEquals(1, mailbox.count(), "the claimant is emailed a fresh link");
    }

    @Test
    @DisplayName("the squatter's outstanding link is invalidated by the takeover")
    void takeoverInvalidatesOldTokens() throws Exception {
        String email = registerClient("squattoken");
        String squatterToken = mailbox.lastToken();
        age(email, 8);
        mailbox.reset();

        assertEquals(201, reRegister(email));

        // If the old link still worked, whoever holds it could verify an account
        // whose password now belongs to somebody else.
        assertNotNull(emailTokenRepository
                        .findByTokenHashWithUser(tokenHasher.hash(squatterToken))
                        .orElseThrow().getInvalidatedAt(),
                "the previous owner's token must be dead");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", squatterToken))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the claimant's fresh token verifies the account")
    void claimantCanVerify() throws Exception {
        String email = registerClient("squatverify");
        age(email, 8);
        mailbox.reset();

        assertEquals(201, reRegister(email));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", mailbox.lastToken()))))
                .andExpect(status().isOk());

        assertTrue(userRepositoryForSupport.findByEmail(email).orElseThrow().isEmailVerified());
    }

    // ------------------------------------------------------------ refused

    @Test
    @DisplayName("an account YOUNGER than 7 days is refused with the ordinary 409")
    void refusedInsideWindow() throws Exception {
        String email = registerClient("squatfresh");
        age(email, 6);
        mailbox.reset();

        // Six days is inside the window: plenty of real users verify on day two.
        assertEquals(409, reRegister(email));
        assertEquals(0, mailbox.count());
    }

    @Test
    @DisplayName("a VERIFIED account is never claimable, however old")
    void refusedWhenVerified() throws Exception {
        String email = registerClient("squatverified");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", mailbox.lastToken()))))
                .andExpect(status().isOk());

        age(email, 400);
        mailbox.reset();

        // A verified account is somebody's, full stop.
        assertEquals(409, reRegister(email));
        assertEquals(0, mailbox.count());
    }

    @Test
    @DisplayName("an account with APPOINTMENTS is never claimable")
    void refusedWithDependentAppointments() throws Exception {
        String clientEmail = registerClient("squatappt");
        User client = age(clientEmail, 30);

        String lawyerEmail = uniqueEmail("squatlawyer");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(lawyerRegistration(lawyerEmail))))
                .andExpect(status().isCreated());
        User lawyerUser = userRepositoryForSupport.findByEmail(lawyerEmail).orElseThrow();
        Lawyer lawyer = lawyerRepository.findByUser(lawyerUser).orElseThrow();

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setLawyer(lawyer);
        appointment.setAppointmentDate(LocalDate.now().plusDays(3));
        appointment.setAppointmentTime(LocalTime.of(11, 0));
        appointment.setConsultationMode(ConsultationMode.ONLINE);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointmentRepository.saveAndFlush(appointment);

        mailbox.reset();

        // Any activity at all means a real person used this account.
        assertEquals(409, reRegister(clientEmail));
        assertEquals(0, mailbox.count());
    }

    @Test
    @DisplayName("a LAWYER account is never claimable by this mechanism")
    void refusedForLawyer() throws Exception {
        String email = uniqueEmail("squatlawyeracct");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(lawyerRegistration(email))))
                .andExpect(status().isCreated());

        age(email, 90);
        mailbox.reset();

        // A LAWYER owns a profile row with a unique bar_council_number, so
        // claiming it would mean deciding what happens to somebody's
        // professional credentials.
        assertEquals(409, reRegister(email));
        assertEquals(0, mailbox.count());
    }

    @Test
    @DisplayName("a DEACTIVATED account is never claimable — that would undo moderation")
    void refusedWhenDeactivated() throws Exception {
        String email = registerClient("squatdisabled");
        User user = age(email, 60);
        user.setActive(false);
        userRepositoryForSupport.saveAndFlush(user);
        mailbox.reset();

        assertEquals(409, reRegister(email));
        assertEquals(0, mailbox.count());
    }
}
