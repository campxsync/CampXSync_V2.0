package com.campx.admissions.model.dto;

import java.util.UUID;

public class AdmissionsFunnelReport {

    private UUID tenantId;
    private UUID intakeId;
    private long totalDrafts;
    private long totalSubmitted;
    private long totalEligible;
    private long totalReviewed;
    private long totalOffered;
    private long totalAccepted;
    private long totalDeclined;
    private long totalExpired;
    private long totalRejected;
    private long totalHandedOver;
    private double conversionRatePercentage;

    public AdmissionsFunnelReport() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(UUID intakeId) {
        this.intakeId = intakeId;
    }

    public long getTotalDrafts() {
        return totalDrafts;
    }

    public void setTotalDrafts(long totalDrafts) {
        this.totalDrafts = totalDrafts;
    }

    public long getTotalSubmitted() {
        return totalSubmitted;
    }

    public void setTotalSubmitted(long totalSubmitted) {
        this.totalSubmitted = totalSubmitted;
    }

    public long getTotalEligible() {
        return totalEligible;
    }

    public void setTotalEligible(long totalEligible) {
        this.totalEligible = totalEligible;
    }

    public long getTotalReviewed() {
        return totalReviewed;
    }

    public void setTotalReviewed(long totalReviewed) {
        this.totalReviewed = totalReviewed;
    }

    public long getTotalOffered() {
        return totalOffered;
    }

    public void setTotalOffered(long totalOffered) {
        this.totalOffered = totalOffered;
    }

    public long getTotalAccepted() {
        return totalAccepted;
    }

    public void setTotalAccepted(long totalAccepted) {
        this.totalAccepted = totalAccepted;
    }

    public long getTotalDeclined() {
        return totalDeclined;
    }

    public void setTotalDeclined(long totalDeclined) {
        this.totalDeclined = totalDeclined;
    }

    public long getTotalExpired() {
        return totalExpired;
    }

    public void setTotalExpired(long totalExpired) {
        this.totalExpired = totalExpired;
    }

    public long getTotalRejected() {
        return totalRejected;
    }

    public void setTotalRejected(long totalRejected) {
        this.totalRejected = totalRejected;
    }

    public long getTotalHandedOver() {
        return totalHandedOver;
    }

    public void setTotalHandedOver(long totalHandedOver) {
        this.totalHandedOver = totalHandedOver;
    }

    public double getConversionRatePercentage() {
        return conversionRatePercentage;
    }

    public void setConversionRatePercentage(double conversionRatePercentage) {
        this.conversionRatePercentage = conversionRatePercentage;
    }
}
