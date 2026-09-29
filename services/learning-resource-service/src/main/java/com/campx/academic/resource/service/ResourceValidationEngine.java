package com.campx.academic.resource.service;

import com.campx.academic.resource.exception.ResourceValidationException;
import com.campx.academic.resource.model.ResourceModels.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Validation engine implementing deterministic checks for ACD-08 Learning Resources:
 * - Resource Type policy (US-004)
 * - Object reference, MIME type, file size and checksum verification (US-008, US-050)
 * - Time window and scope validation for access policies (US-052, US-055)
 */
public class ResourceValidationEngine {

    private static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "audio/mpeg",
            "audio/mp4",
            "audio/ogg",
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/svg+xml",
            "text/plain",
            "text/html",
            "application/json",
            "application/zip"
    ));

    private static final Pattern HEX_CHECKSUM_PATTERN = Pattern.compile("^[a-fA-F0-9]{32,64}$");

    /**
     * Validates create resource request metadata.
     */
    public void validateCreateResource(CreateResourceRequest req) {
        if (req == null) {
            throw new ResourceValidationException("Request payload cannot be empty");
        }
        if (req.resourceCode == null || req.resourceCode.trim().isEmpty()) {
            throw new ResourceValidationException("resourceCode is required and cannot be blank");
        }
        if (req.title == null || req.title.trim().isEmpty()) {
            throw new ResourceValidationException("title is required and cannot be blank");
        }
        validateResourceType(req.resourceType);

        // Validate content reference if supplied in creation
        if (req.storageObjectRef != null && !req.storageObjectRef.trim().isEmpty()) {
            validateContentMetadata(req.mimeType, req.fileSize, req.checksum);
        }
    }

    /**
     * Validates supported resource type enum.
     */
    public ResourceType validateResourceType(String resourceTypeStr) {
        if (resourceTypeStr == null || resourceTypeStr.trim().isEmpty()) {
            throw new ResourceValidationException("resourceType is required");
        }
        try {
            return ResourceType.valueOf(resourceTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceValidationException("Unsupported resourceType: '" + resourceTypeStr +
                    "'. Allowed values: " + Arrays.toString(ResourceType.values()));
        }
    }

    /**
     * Validates binary content metadata: MIME type, non-negative size, and hex checksum (US-008, US-050).
     */
    public void validateContentMetadata(String mimeType, Long fileSize, String checksum) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new ResourceValidationException("mimeType is required for binary content references");
        }
        String normalizedMime = mimeType.trim().toLowerCase();
        if (!SUPPORTED_MIME_TYPES.contains(normalizedMime)) {
            throw new ResourceValidationException("Unsupported MIME type: '" + mimeType +
                    "'. Supported formats include PDF, Office documents, MP4, WebM, audio, images, and text.");
        }

        if (fileSize == null || fileSize < 0) {
            throw new ResourceValidationException("fileSize must be non-negative (received: " + fileSize + ")");
        }

        if (checksum == null || checksum.trim().isEmpty()) {
            throw new ResourceValidationException("checksum/contentHash is required for immutable content integrity verification");
        }
        String cleanChecksum = checksum.trim();
        if (!HEX_CHECKSUM_PATTERN.matcher(cleanChecksum).matches()) {
            throw new ResourceValidationException("Invalid checksum format: '" + checksum +
                    "'. Must be a 32 to 64 character hex string (e.g. SHA-256).");
        }
    }

    /**
     * Validates create version request metadata.
     */
    public void validateCreateVersion(CreateVersionRequest req) {
        if (req == null) {
            throw new ResourceValidationException("Version payload cannot be empty");
        }
        if (req.storageObjectRef == null || req.storageObjectRef.trim().isEmpty()) {
            throw new ResourceValidationException("storageObjectRef is required for new version");
        }
        validateContentMetadata(req.mimeType, req.fileSize, req.checksum);
    }

    /**
     * Validates access grant time window and scopes (US-052, US-055).
     */
    public void validateAccessGrant(CreateAccessGrantRequest req) {
        if (req == null) {
            throw new ResourceValidationException("Access grant request cannot be empty");
        }
        if (req.principalType == null) {
            throw new ResourceValidationException("principalType is required (USER, ROLE, GROUP)");
        }
        try {
            PrincipalType.valueOf(req.principalType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceValidationException("Invalid principalType: " + req.principalType);
        }

        if (req.principalId == null || req.principalId.trim().isEmpty()) {
            throw new ResourceValidationException("principalId is required");
        }

        if (req.permission == null) {
            throw new ResourceValidationException("permission is required (VIEW, DOWNLOAD, MANAGE)");
        }
        try {
            Permission.valueOf(req.permission.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceValidationException("Invalid permission: " + req.permission);
        }

        if (req.effect != null) {
            try {
                AccessEffect.valueOf(req.effect.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResourceValidationException("Invalid effect: " + req.effect + " (must be ALLOW or DENY)");
            }
        }

        if (req.scopeType != null) {
            try {
                ScopeType.valueOf(req.scopeType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResourceValidationException("Invalid scopeType: " + req.scopeType);
            }
        }

        // Validate time window
        Instant from = null;
        Instant to = null;
        if (req.validFrom != null && !req.validFrom.trim().isEmpty()) {
            try {
                from = Instant.parse(req.validFrom.trim());
            } catch (DateTimeParseException e) {
                throw new ResourceValidationException("Invalid validFrom ISO timestamp format: " + req.validFrom);
            }
        }
        if (req.validTo != null && !req.validTo.trim().isEmpty()) {
            try {
                to = Instant.parse(req.validTo.trim());
            } catch (DateTimeParseException e) {
                throw new ResourceValidationException("Invalid validTo ISO timestamp format: " + req.validTo);
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResourceValidationException("Invalid time window: validFrom (" + req.validFrom +
                    ") cannot be after validTo (" + req.validTo + ")");
        }
    }
}
