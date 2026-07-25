package com.arshraj.vakilconnect.appointment.dto;

public class LawyerDashboardResponse {

    private long pendingAppointments;
    private long acceptedAppointments;
    private long completedAppointments;
    private long todaysAppointments;

    private boolean profileVerified;
    private double averageRating;
    private int totalReviews;

    public LawyerDashboardResponse() {
    }

    public long getPendingAppointments() {
        return pendingAppointments;
    }

    public void setPendingAppointments(long pendingAppointments) {
        this.pendingAppointments = pendingAppointments;
    }

    public long getAcceptedAppointments() {
        return acceptedAppointments;
    }

    public void setAcceptedAppointments(long acceptedAppointments) {
        this.acceptedAppointments = acceptedAppointments;
    }

    public long getCompletedAppointments() {
        return completedAppointments;
    }

    public void setCompletedAppointments(long completedAppointments) {
        this.completedAppointments = completedAppointments;
    }

    public long getTodaysAppointments() {
        return todaysAppointments;
    }

    public void setTodaysAppointments(long todaysAppointments) {
        this.todaysAppointments = todaysAppointments;
    }

    public boolean isProfileVerified() {
        return profileVerified;
    }

    public void setProfileVerified(boolean profileVerified) {
        this.profileVerified = profileVerified;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(int totalReviews) {
        this.totalReviews = totalReviews;
    }
}
