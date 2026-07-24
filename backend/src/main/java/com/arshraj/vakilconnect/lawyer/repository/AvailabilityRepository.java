package com.arshraj.vakilconnect.lawyer.repository;

import com.arshraj.vakilconnect.lawyer.entity.Availability;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByLawyerOrderByDayOfWeekAscStartTimeAsc(Lawyer lawyer);

    Optional<Availability> findByIdAndLawyer(UUID id, Lawyer lawyer);

    boolean existsByLawyerAndDayOfWeekAndStartTimeAndEndTime(
            Lawyer lawyer, DayOfWeek dayOfWeek, java.time.LocalTime startTime, java.time.LocalTime endTime);

    List<Availability> findByLawyerAndDayOfWeekAndAvailableTrue(Lawyer lawyer, DayOfWeek dayOfWeek);
}
