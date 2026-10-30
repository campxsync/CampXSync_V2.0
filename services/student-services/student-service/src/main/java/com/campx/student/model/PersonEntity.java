package com.campx.student.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapping directly to {@code core.persons} in PostgreSQL.
 * Represents base human bio-demographic identity.
 */
public class PersonEntity {

    private UUID id;
    private UUID tenantId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String preferredName;
    private String displayName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String bloodGroup;
    private String nationality;
    private String maritalStatus;
    private String primaryEmail;
    private String primaryPhone;
    private UUID photoDocumentId;
    private UUID permanentAddressId;
    private UUID currentAddressId;
    private String status = "ACTIVE";
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime deletedAt;

    public PersonEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPreferredName() { return preferredName; }
    public void setPreferredName(String preferredName) { this.preferredName = preferredName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }

    public String getPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(String primaryPhone) { this.primaryPhone = primaryPhone; }

    public UUID getPhotoDocumentId() { return photoDocumentId; }
    public void setPhotoDocumentId(UUID photoDocumentId) { this.photoDocumentId = photoDocumentId; }

    public UUID getPermanentAddressId() { return permanentAddressId; }
    public void setPermanentAddressId(UUID permanentAddressId) { this.permanentAddressId = permanentAddressId; }

    public UUID getCurrentAddressId() { return currentAddressId; }
    public void setCurrentAddressId(UUID currentAddressId) { this.currentAddressId = currentAddressId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
