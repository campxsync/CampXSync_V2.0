package com.campx.student.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for alternate/institutional identifiers with field-level masking support (STM01-US-008, STM01-US-009).
 */
public class PersonIdentifierDto {

    private UUID id;
    private String idType;
    private String identifierValue;
    private String maskedValue;
    private String issuingAuthority;
    private String validFrom;
    private String validTo;
    private boolean verified;

    public PersonIdentifierDto() {}

    public static String computeMaskedValue(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "****";
        }
        String clean = raw.trim();
        if (clean.length() <= 4) {
            return "****" + clean;
        }
        String suffix = clean.substring(clean.length() - 4);
        return "XXXX-XXXX-" + suffix;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }

    public String getMaskedValue() { return maskedValue; }
    public void setMaskedValue(String maskedValue) { this.maskedValue = maskedValue; }

    public String getIssuingAuthority() { return issuingAuthority; }
    public void setIssuingAuthority(String issuingAuthority) { this.issuingAuthority = issuingAuthority; }

    public String getValidFrom() { return validFrom; }
    public void setValidFrom(String validFrom) { this.validFrom = validFrom; }

    public String getValidTo() { return validTo; }
    public void setValidTo(String validTo) { this.validTo = validTo; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
