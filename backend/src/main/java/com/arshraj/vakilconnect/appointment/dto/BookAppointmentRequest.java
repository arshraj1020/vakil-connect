package com.arshraj.vakilconnect.appointment.dto;

import com.arshraj.vakilconnect.appointment.enums.ConsultationMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class BookAppointmentRequest {

    @NotNull(message = "Lawyer is required")
    private UUID lawyerId;

    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDate appointmentDate;

    @NotNull(message = "Appointment time is required")
    private LocalTime appointmentTime;

    @NotNull(message = "Consultation mode is required")
    private ConsultationMode consultationMode;

    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;

    public BookAppointmentRequest() {
    }

    public UUID getLawyerId() {
        return lawyerId;
    }

    public void setLawyerId(UUID lawyerId) {
        this.lawyerId = lawyerId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public ConsultationMode getConsultationMode() {
        return consultationMode;
    }

    public void setConsultationMode(ConsultationMode consultationMode) {
        this.consultationMode = consultationMode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
