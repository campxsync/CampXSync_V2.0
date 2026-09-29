package com.campx.academic.resource.exception;

/**
 * Thrown when resource metadata, checksum, MIME type, file size or date windows fail validation (HTTP 422).
 */
public class ResourceValidationException extends ResourceException {

    public ResourceValidationException(String message) {
        super(422, "ACD_RESOURCE_VALIDATION_ERROR", message);
    }
}
