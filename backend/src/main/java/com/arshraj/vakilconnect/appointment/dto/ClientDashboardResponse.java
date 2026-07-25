package com.arshraj.vakilconnect.appointment.dto;

public class ClientDashboardResponse {

    private long totalAppointments;
    private long upcomingAppointments;
    private long completedAppointments;
    private long cancelledAppointments;

    /** Nearest upcoming appointment, or null if the client has none. */
    private AppointmentResponse nextAppointment;

    public ClientDashboardResponse() {
    }

    public long getTotalAppointments() {
        return totalAppointments;
    }

    public void setTotalAppointments(long totalAppointments) {
        this.totalAppointments = totalAppointments;
    }

    public long getUpcomingAppointments() {
        return upcomingAppointments;
    }

    public void setUpcomingAppointments(long upcomingAppointments) {
        this.upcomingAppointments = upcomingAppointments;
    }

    public long getCompletedAppointments() {
        return completedAppointments;
    }

    public void setCompletedAppointments(long completedAppointments) {
        this.completedAppointments = completedAppointments;
    }

    public long getCancelledAppointments() {
        return cancelledAppointments;
    }

    public void setCancelledAppointments(long cancelledAppointments) {
        this.cancelledAppointments = cancelledAppointments;
    }

    public AppointmentResponse getNextAppointment() {
        return nextAppointment;
    }

    public void setNextAppointment(AppointmentResponse nextAppointment) {
        this.nextAppointment = nextAppointment;
    }
}
