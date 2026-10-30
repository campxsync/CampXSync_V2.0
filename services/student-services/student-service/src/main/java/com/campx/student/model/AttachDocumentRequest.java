package com.campx.student.model;

import java.time.LocalDate;

/**
 * Ingress DTO for associating document metadata references (STM01-US-020).
 */
public class AttachDocumentRequest {

    private String title;
    private String documentType;
    private String storageBucket;
    private String storagePath;
    private String mimeType;
    private long sizeBytes;
    private String classification = "CONFIDENTIAL";
    private String validTo;

    public AttachDocumentRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public String getValidTo() { return validTo; }
    public void setValidTo(String validTo) { this.validTo = validTo; }

    public LocalDate getParsedValidTo() {
        if (validTo != null && !validTo.trim().isEmpty()) {
            try {
                return LocalDate.parse(validTo.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
