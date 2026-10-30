package com.campx.student.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ingress DTO for student matriculation and enrollment.
 */
public class EnrollStudentRequest {

    // Person bio-demographics (maps to core.persons)
    private String firstName;
    private String middleName;
    private String lastName;
    private String displayName;
    private String dateOfBirth; // ISO-8601 string, e.g. "2006-05-15"
    private Gender gender = Gender.U;
    private String primaryEmail;
    private String primaryPhone;

    // Student academic attributes (maps to stu.students)
    private String enrollmentNo;
    private String admissionNo;
    private String admissionDate; // ISO-8601 string, e.g. "2026-08-01"
    private UUID collegeId;
    private UUID programId;
    private UUID admissionAcademicYearId;

    // Optional cohort assignment (delegated via BatchAssignmentContract)
    private UUID batchId;
    private String rollNo;

    // Optional primary guardian
    private GuardianRequest guardian;

    public EnrollStudentRequest() {}

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDisplayName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim();
        }
        StringBuilder sb = new StringBuilder();
        if (firstName != null) sb.append(firstName.trim());
        if (middleName != null && !middleName.trim().isEmpty()) sb.append(" ").append(middleName.trim());
        if (lastName != null && !lastName.trim().isEmpty()) sb.append(" ").append(lastName.trim());
        return sb.toString().trim();
    }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth != null ? dateOfBirth.toString() : null;
    }
    public LocalDate getParsedDateOfBirth() {
        if (dateOfBirth == null || dateOfBirth.trim().isEmpty()) return null;
        try { return LocalDate.parse(dateOfBirth.trim()); } catch (Exception e) { return null; }
    }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }

    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }

    public String getAdmissionDate() { return admissionDate; }
    public void setAdmissionDate(String admissionDate) { this.admissionDate = admissionDate; }
    public void setAdmissionDate(LocalDate admissionDate) {
        this.admissionDate = admissionDate != null ? admissionDate.toString() : null;
    }
    public LocalDate getParsedAdmissionDate() {
        if (admissionDate == null || admissionDate.trim().isEmpty()) return null;
        try { return LocalDate.parse(admissionDate.trim()); } catch (Exception e) { return null; }
    }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public UUID getAdmissionAcademicYearId() { return admissionAcademicYearId; }
    public void setAdmissionAcademicYearId(UUID admissionAcademicYearId) { this.admissionAcademicYearId = admissionAcademicYearId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public GuardianRequest getGuardian() { return guardian; }
    public void setGuardian(GuardianRequest guardian) { this.guardian = guardian; }

    public static class GuardianRequest {
        private String firstName;
        private String lastName;
        private String relationship;
        private String primaryEmail;
        private String primaryPhone;
        private boolean isPrimary = true;
        private boolean canViewAcademics = true;
        private boolean canViewFees = true;
        private boolean canPayFees = false;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getRelationship() { return relationship; }
        public void setRelationship(String relationship) { this.relationship = relationship; }

        public String getPrimaryEmail() { return primaryEmail; }
        public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }

        public String getPrimaryPhone() { return primaryPhone; }
        public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

        public boolean isPrimary() { return isPrimary; }
        public void setPrimary(boolean primary) { isPrimary = primary; }

        public boolean isCanViewAcademics() { return canViewAcademics; }
        public void setCanViewAcademics(boolean canViewAcademics) { this.canViewAcademics = canViewAcademics; }

        public boolean isCanViewFees() { return canViewFees; }
        public void setCanViewFees(boolean canViewFees) { this.canViewFees = canViewFees; }

        public boolean isCanPayFees() { return canPayFees; }
        public void setCanPayFees(boolean canPayFees) { this.canPayFees = canPayFees; }
    }
}
