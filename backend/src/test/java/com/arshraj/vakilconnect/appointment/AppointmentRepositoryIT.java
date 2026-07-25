package com.arshraj.vakilconnect.appointment;

import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.appointment.enums.ConsultationMode;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.support.AbstractIntegrationTest;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Repository-level tests for the appointment queries, run against the real
 * PostgreSQL schema (Flyway V1 + V2) so the partial unique index is exercised.
 *
 * Entities are built directly rather than through HTTP; each test creates its
 * own client/lawyer so per-user queries are isolated and no cleanup is needed.
 */
@DisplayName("Appointment repository queries")
class AppointmentRepositoryIT extends AbstractIntegrationTest {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.ACCEPTED);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------- fixtures

    private User newClient() {
        User user = new User();
        user.setFullName("Repo Test Client");
        user.setEmail("repo_client_" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setPhoneNumber("9876543210");
        user.setRole(Role.CLIENT);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Lawyer newLawyer() {
        User user = new User();
        user.setFullName("Repo Test Lawyer");
        user.setEmail("repo_lawyer_" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setPhoneNumber("9876543211");
        user.setRole(Role.LAWYER);
        user.setActive(true);
        User savedUser = userRepository.save(user);

        Lawyer lawyer = new Lawyer();
        lawyer.setUser(savedUser);
        lawyer.setBarCouncilNumber("BC_" + UUID.randomUUID());
        lawyer.setExperienceYears(5);
        lawyer.setBio("Repo test lawyer");
        lawyer.setConsultationFee(new BigDecimal("1500.00"));
        lawyer.setCity("Mumbai");
        lawyer.setOfficeAddress("123 Court Road");
        lawyer.setVerified(true);
        return lawyerRepository.save(lawyer);
    }

    private Appointment newAppointment(User client, Lawyer lawyer,
                                       LocalDate date, LocalTime time,
                                       AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setLawyer(lawyer);
        appointment.setAppointmentDate(date);
        appointment.setAppointmentTime(time);
        appointment.setConsultationMode(ConsultationMode.ONLINE);
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private LocalDate daysAhead(int days) {
        return LocalDate.now().plusDays(days);
    }

    // ------------------------------------------------- R1-R3: scoped finders

    @Test
    @DisplayName("R1: findByClient returns only that client's appointments")
    void findByClientIsScopedToClient() {
        User clientA = newClient();
        User clientB = newClient();
        Lawyer lawyer = newLawyer();

        newAppointment(clientA, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(clientA, lawyer, daysAhead(11), LocalTime.of(9, 0), AppointmentStatus.ACCEPTED);
        newAppointment(clientA, lawyer, daysAhead(12), LocalTime.of(9, 0), AppointmentStatus.COMPLETED);
        newAppointment(clientB, lawyer, daysAhead(13), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        List<Appointment> result =
                appointmentRepository.findByClientOrderByAppointmentDateDescAppointmentTimeDesc(clientA);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(a -> a.getClient().getId().equals(clientA.getId()));
    }

    @Test
    @DisplayName("R2: findByClient orders by date DESC then time DESC")
    void findByClientOrdering() {
        User client = newClient();
        Lawyer lawyer = newLawyer();

        LocalDate earlier = daysAhead(20);
        LocalDate later = daysAhead(25);

        newAppointment(client, lawyer, later, LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, later, LocalTime.of(14, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, earlier, LocalTime.of(10, 0), AppointmentStatus.PENDING);

        List<Appointment> result =
                appointmentRepository.findByClientOrderByAppointmentDateDescAppointmentTimeDesc(client);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAppointmentDate()).isEqualTo(later);
        assertThat(result.get(0).getAppointmentTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(result.get(1).getAppointmentDate()).isEqualTo(later);
        assertThat(result.get(1).getAppointmentTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(2).getAppointmentDate()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("R3: findByLawyer returns only that lawyer's appointments")
    void findByLawyerIsScopedToLawyer() {
        User client = newClient();
        Lawyer lawyerA = newLawyer();
        Lawyer lawyerB = newLawyer();

        newAppointment(client, lawyerA, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyerA, daysAhead(11), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyerB, daysAhead(12), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        List<Appointment> result =
                appointmentRepository.findByLawyerOrderByAppointmentDateDescAppointmentTimeDesc(lawyerA);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(a -> a.getLawyer().getId().equals(lawyerA.getId()));
    }

    // --------------------------------------------- R4-R6: ownership lookups

    @Test
    @DisplayName("R4/R5: findByIdAndClient succeeds for the owner and is empty for another client")
    void findByIdAndClientEnforcesOwnership() {
        User owner = newClient();
        User other = newClient();
        Lawyer lawyer = newLawyer();

        Appointment appointment =
                newAppointment(owner, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        assertThat(appointmentRepository.findByIdAndClient(appointment.getId(), owner)).isPresent();
        assertThat(appointmentRepository.findByIdAndClient(appointment.getId(), other)).isEmpty();
    }

    @Test
    @DisplayName("R6: findByIdAndLawyer succeeds for the owner and is empty for another lawyer")
    void findByIdAndLawyerEnforcesOwnership() {
        User client = newClient();
        Lawyer owner = newLawyer();
        Lawyer other = newLawyer();

        Appointment appointment =
                newAppointment(client, owner, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        assertThat(appointmentRepository.findByIdAndLawyer(appointment.getId(), owner)).isPresent();
        assertThat(appointmentRepository.findByIdAndLawyer(appointment.getId(), other)).isEmpty();
    }

    // ------------------------------------------------ R7-R9: client counts

    @Test
    @DisplayName("R7: countByClient counts all of the client's appointments")
    void countByClient() {
        User client = newClient();
        Lawyer lawyer = newLawyer();

        newAppointment(client, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(11), LocalTime.of(9, 0), AppointmentStatus.COMPLETED);
        newAppointment(client, lawyer, daysAhead(12), LocalTime.of(9, 0), AppointmentStatus.CANCELLED);

        assertThat(appointmentRepository.countByClient(client)).isEqualTo(3);
    }

    @Test
    @DisplayName("R8: countByClientAndStatus counts per status")
    void countByClientAndStatus() {
        User client = newClient();
        Lawyer lawyer = newLawyer();

        newAppointment(client, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.COMPLETED);
        newAppointment(client, lawyer, daysAhead(11), LocalTime.of(9, 0), AppointmentStatus.COMPLETED);
        newAppointment(client, lawyer, daysAhead(12), LocalTime.of(9, 0), AppointmentStatus.CANCELLED);

        assertThat(appointmentRepository.countByClientAndStatus(client, AppointmentStatus.COMPLETED))
                .isEqualTo(2);
        assertThat(appointmentRepository.countByClientAndStatus(client, AppointmentStatus.CANCELLED))
                .isEqualTo(1);
        assertThat(appointmentRepository.countByClientAndStatus(client, AppointmentStatus.PENDING))
                .isZero();
    }

    @Test
    @DisplayName("R9: upcoming count includes today and excludes past and terminal statuses")
    void countUpcomingForClient() {
        User client = newClient();
        Lawyer lawyer = newLawyer();
        LocalDate today = LocalDate.now();

        newAppointment(client, lawyer, today.minusDays(3), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, today, LocalTime.of(10, 0), AppointmentStatus.ACCEPTED);
        newAppointment(client, lawyer, daysAhead(5), LocalTime.of(11, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(6), LocalTime.of(12, 0), AppointmentStatus.CANCELLED);

        long upcoming = appointmentRepository
                .countByClientAndAppointmentDateGreaterThanEqualAndStatusIn(client, today, ACTIVE_STATUSES);

        assertThat(upcoming).isEqualTo(2);
    }

    // ------------------------------------------- R10-R11: next appointment

    @Test
    @DisplayName("R10: findFirst returns the nearest upcoming appointment by date then time")
    void findFirstUpcomingReturnsNearest() {
        User client = newClient();
        Lawyer lawyer = newLawyer();
        LocalDate today = LocalDate.now();

        newAppointment(client, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(5), LocalTime.of(15, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(5), LocalTime.of(11, 0), AppointmentStatus.PENDING);

        Optional<Appointment> next = appointmentRepository
                .findFirstByClientAndAppointmentDateGreaterThanEqualAndStatusInOrderByAppointmentDateAscAppointmentTimeAsc(
                        client, today, ACTIVE_STATUSES);

        assertThat(next).isPresent();
        assertThat(next.get().getAppointmentDate()).isEqualTo(daysAhead(5));
        assertThat(next.get().getAppointmentTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("R11: findFirst is empty when there is nothing upcoming")
    void findFirstUpcomingEmptyWhenNone() {
        User client = newClient();
        Lawyer lawyer = newLawyer();
        LocalDate today = LocalDate.now();

        newAppointment(client, lawyer, today.minusDays(5), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(5), LocalTime.of(9, 0), AppointmentStatus.CANCELLED);

        Optional<Appointment> next = appointmentRepository
                .findFirstByClientAndAppointmentDateGreaterThanEqualAndStatusInOrderByAppointmentDateAscAppointmentTimeAsc(
                        client, today, ACTIVE_STATUSES);

        assertThat(next).isEmpty();
    }

    // ----------------------------------------------- R12-R13: lawyer counts

    @Test
    @DisplayName("R12: countByLawyerAndStatus counts per status for that lawyer")
    void countByLawyerAndStatus() {
        Lawyer lawyer = newLawyer();
        User client = newClient();

        newAppointment(client, lawyer, daysAhead(10), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(11), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(12), LocalTime.of(9, 0), AppointmentStatus.ACCEPTED);

        assertThat(appointmentRepository.countByLawyerAndStatus(lawyer, AppointmentStatus.PENDING))
                .isEqualTo(2);
        assertThat(appointmentRepository.countByLawyerAndStatus(lawyer, AppointmentStatus.ACCEPTED))
                .isEqualTo(1);
        assertThat(appointmentRepository.countByLawyerAndStatus(lawyer, AppointmentStatus.COMPLETED))
                .isZero();
    }

    @Test
    @DisplayName("R13: today's count includes only active appointments dated today")
    void countTodaysForLawyer() {
        Lawyer lawyer = newLawyer();
        User client = newClient();
        LocalDate today = LocalDate.now();

        newAppointment(client, lawyer, today, LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, today, LocalTime.of(10, 0), AppointmentStatus.CANCELLED);
        newAppointment(client, lawyer, today.plusDays(1), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        long todays = appointmentRepository
                .countByLawyerAndAppointmentDateAndStatusIn(lawyer, today, ACTIVE_STATUSES);

        assertThat(todays).isEqualTo(1);
    }

    // ------------------------------------------------------ R14: global count

    @Test
    @DisplayName("R14: countByStatus reflects newly added rows (delta, since the count is global)")
    void countByStatusDelta() {
        User client = newClient();
        Lawyer lawyer = newLawyer();

        long before = appointmentRepository.countByStatus(AppointmentStatus.PENDING);

        newAppointment(client, lawyer, daysAhead(30), LocalTime.of(9, 0), AppointmentStatus.PENDING);
        newAppointment(client, lawyer, daysAhead(31), LocalTime.of(9, 0), AppointmentStatus.PENDING);

        long after = appointmentRepository.countByStatus(AppointmentStatus.PENDING);

        assertThat(after - before).isEqualTo(2);
    }

    // --------------------------------------- R15-R16: partial unique index

    @Test
    @DisplayName("R15: the partial unique index rejects a second ACTIVE appointment in the same slot")
    void partialUniqueIndexRejectsDuplicateActiveSlot() {
        User clientA = newClient();
        User clientB = newClient();
        Lawyer lawyer = newLawyer();
        LocalDate date = daysAhead(40);
        LocalTime time = LocalTime.of(10, 0);

        newAppointment(clientA, lawyer, date, time, AppointmentStatus.PENDING);

        Appointment duplicate = new Appointment();
        duplicate.setClient(clientB);
        duplicate.setLawyer(lawyer);
        duplicate.setAppointmentDate(date);
        duplicate.setAppointmentTime(time);
        duplicate.setConsultationMode(ConsultationMode.ONLINE);
        duplicate.setStatus(AppointmentStatus.ACCEPTED);

        assertThrows(DataIntegrityViolationException.class,
                () -> appointmentRepository.saveAndFlush(duplicate));
    }

    @Test
    @DisplayName("R16: the slot is free again once the previous appointment is terminal")
    void partialUniqueIndexAllowsRebookAfterTerminalStatus() {
        User client = newClient();
        Lawyer lawyer = newLawyer();
        LocalDate date = daysAhead(41);
        LocalTime time = LocalTime.of(10, 0);

        newAppointment(client, lawyer, date, time, AppointmentStatus.CANCELLED);

        Appointment rebooked =
                newAppointment(client, lawyer, date, time, AppointmentStatus.PENDING);

        assertThat(rebooked.getId()).isNotNull();
        assertThat(rebooked.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }
}
