package com.arshraj.vakilconnect.appointment.service;

import com.arshraj.vakilconnect.appointment.dto.AppointmentResponse;
import com.arshraj.vakilconnect.appointment.dto.BookAppointmentRequest;
import com.arshraj.vakilconnect.appointment.dto.ClientDashboardResponse;
import com.arshraj.vakilconnect.appointment.dto.LawyerDashboardResponse;

import java.util.List;
import java.util.UUID;

public interface AppointmentService {

    AppointmentResponse bookAppointment(String clientEmail, BookAppointmentRequest request);

    List<AppointmentResponse> getClientAppointments(String clientEmail);

    AppointmentResponse cancelAppointment(String clientEmail, UUID appointmentId);

    List<AppointmentResponse> getLawyerAppointments(String lawyerEmail);

    AppointmentResponse acceptAppointment(String lawyerEmail, UUID appointmentId);

    AppointmentResponse rejectAppointment(String lawyerEmail, UUID appointmentId);

    AppointmentResponse completeAppointment(String lawyerEmail, UUID appointmentId);

    ClientDashboardResponse getCurrentClientDashboard(String clientEmail);

    LawyerDashboardResponse getCurrentLawyerDashboard(String lawyerEmail);
}
