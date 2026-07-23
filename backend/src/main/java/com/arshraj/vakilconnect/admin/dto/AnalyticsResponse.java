package com.arshraj.vakilconnect.admin.dto;

public class AnalyticsResponse {

    private long totalUsers;
    private long totalClients;
    private long totalLawyers;
    private long totalAdmins;

    private long verifiedLawyers;
    private long unverifiedLawyers;

    private long totalAppointments;
    private long pendingAppointments;
    private long acceptedAppointments;
    private long completedAppointments;
    private long rejectedAppointments;
    private long cancelledAppointments;

    private long totalReviews;
    private double averagePlatformRating;

    public AnalyticsResponse() {
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalClients() {
        return totalClients;
    }

    public void setTotalClients(long totalClients) {
        this.totalClients = totalClients;
    }

    public long getTotalLawyers() {
        return totalLawyers;
    }

    public void setTotalLawyers(long totalLawyers) {
        this.totalLawyers = totalLawyers;
    }

    public long getTotalAdmins() {
        return totalAdmins;
    }

    public void setTotalAdmins(long totalAdmins) {
        this.totalAdmins = totalAdmins;
    }

    public long getVerifiedLawyers() {
        return verifiedLawyers;
    }

    public void setVerifiedLawyers(long verifiedLawyers) {
        this.verifiedLawyers = verifiedLawyers;
    }

    public long getUnverifiedLawyers() {
        return unverifiedLawyers;
    }

    public void setUnverifiedLawyers(long unverifiedLawyers) {
        this.unverifiedLawyers = unverifiedLawyers;
    }

    public long getTotalAppointments() {
        return totalAppointments;
    }

    public void setTotalAppointments(long totalAppointments) {
        this.totalAppointments = totalAppointments;
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

    public long getRejectedAppointments() {
        return rejectedAppointments;
    }

    public void setRejectedAppointments(long rejectedAppointments) {
        this.rejectedAppointments = rejectedAppointments;
    }

    public long getCancelledAppointments() {
        return cancelledAppointments;
    }

    public void setCancelledAppointments(long cancelledAppointments) {
        this.cancelledAppointments = cancelledAppointments;
    }

    public long getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(long totalReviews) {
        this.totalReviews = totalReviews;
    }

    public double getAveragePlatformRating() {
        return averagePlatformRating;
    }

    public void setAveragePlatformRating(double averagePlatformRating) {
        this.averagePlatformRating = averagePlatformRating;
    }
}
