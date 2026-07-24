package com.arshraj.vakilconnect.appointment.repository;

import com.arshraj.vakilconnect.appointment.entity.Appointment;
import com.arshraj.vakilconnect.appointment.enums.AppointmentStatus;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByClientOrderByAppointmentDateDescAppointmentTimeDesc(User client);

    List<Appointment> findByLawyerOrderByAppointmentDateDescAppointmentTimeDesc(Lawyer lawyer);

    Optional<Appointment> findByIdAndClient(UUID id, User client);

    Optional<Appointment> findByIdAndLawyer(UUID id, Lawyer lawyer);

    long countByStatus(AppointmentStatus status);

    boolean existsByLawyerAndAppointmentDateAndAppointmentTimeAndStatusIn(
            Lawyer lawyer,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Collection<AppointmentStatus> statuses);
}
