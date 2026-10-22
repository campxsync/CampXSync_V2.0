package com.campx.student.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Egress DTO representing the complete composite student aggregate profile.
 */
public class StudentProfileResponse {

    private UUID id;
    private UUID tenantId;
    private UUID personId;
    private UUID collegeId;
    private UUID programId;
    private String enrollmentNo;
    private String admissionNo;
    private String admissionDate;
    private UUID admissionAcademicYearId;
    private StudentStatus status;

    // Person attributes
    private String firstName;
    private String middleName;
    private String lastName;
    private String displayName;
    private String dateOfBirth;
    private Gender gender;
    private String primaryEmail;
    private String primaryPhone;

    // Guardians
    private List<GuardianSummary> guardians = new ArrayList<>();

    // Timestamps
    private String createdAt;
    private String updatedAt;

    public StudentProfileResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }

    public String getAdmissionDate() { return admissionDate; }
    public void setAdmissionDate(String admissionDate) { this.admissionDate = admissionDate; }
    public void setAdmissionDate(LocalDate admissionDate) {
        this.admissionDate = admissionDate != null ? admissionDate.toString() : null;
    }

    public UUID getAdmissionAcademicYearId() { return admissionAcademicYearId; }
    public void setAdmissionAcademicYearId(UUID admissionAcademicYearId) { this.admissionAcademicYearId = admissionAcademicYearId; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth != null ? dateOfBirth.toString() : null;
    }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }

    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

    public List<GuardianSummary> getGuardians() { return guardians; }
    public void setGuardians(List<GuardianSummary> guardians) { this.guardians = guardians; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt != null ? createdAt.toString() : null;
    }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt != null ? updatedAt.toString() : null;
    }

    public static class GuardianSummary {
        private UUID guardianshipId;
        private UUID guardianPersonId;
        private String displayName;
        private String relationship;
        private boolean isPrimary;
        private String primaryPhone;
        private String primaryEmail;

        public UUID getGuardianshipId() { return guardianshipId; }
        public void setGuardianshipId(UUID guardianshipId) { this.guardianshipId = guardianshipId; }

        public UUID getGuardianPersonId() { return guardianPersonId; }
        public void setGuardianPersonId(UUID guardianPersonId) { this.guardianPersonId = guardianPersonId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getRelationship() { return relationship; }
        public void setRelationship(String relationship) { this.relationship = relationship; }

        public boolean isPrimary() { return isPrimary; }
        public void setPrimary(boolean primary) { isPrimary = primary; }

        public String getPrimaryPhone() { return primaryPhone; }
        public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

        public String getPrimaryEmail() { return primaryEmail; }
        public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }
    }
}
