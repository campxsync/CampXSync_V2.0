package com.campx.student.model;

import java.util.Map;
import java.util.UUID;

/**
 * Ingress DTO for partial profile updates on allowlisted fields (STM01-US-004).
 * Supports optimistic concurrency control via {@code rowVersion}.
 * Protected fields (legal name, date of birth, enrollment number, status)
 * cannot be mutated through this generic patch endpoint.
 */
public class UpdateStudentProfileRequest {

    private String preferredName;
    private String primaryPhone;
    private String bloodGroup;
    private String nationality;
    private String maritalStatus;
    private UUID photoDocumentId;
    private String locale;
    private String timezone;
    private Map<String, Object> communicationPreferences;
    private Integer rowVersion;

    // Disallowed fields for validation guard
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String enrollmentNo;
    private String status;

    public UpdateStudentProfileRequest() {}

    public String getPreferredName() { return preferredName; }
    public void setPreferredName(String preferredName) { this.preferredName = preferredName; }

    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public UUID getPhotoDocumentId() { return photoDocumentId; }
    public void setPhotoDocumentId(UUID photoDocumentId) { this.photoDocumentId = photoDocumentId; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public Map<String, Object> getCommunicationPreferences() { return communicationPreferences; }
    public void setCommunicationPreferences(Map<String, Object> communicationPreferences) {
        this.communicationPreferences = communicationPreferences;
    }

    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * Checks if this update request attempts to modify protected fields directly.
     */
    public boolean attemptsProtectedFieldUpdate() {
        return (firstName != null && !firstName.trim().isEmpty())
                || (lastName != null && !lastName.trim().isEmpty())
                || (dateOfBirth != null && !dateOfBirth.trim().isEmpty())
                || (enrollmentNo != null && !enrollmentNo.trim().isEmpty())
                || (status != null && !status.trim().isEmpty());
    }
}
