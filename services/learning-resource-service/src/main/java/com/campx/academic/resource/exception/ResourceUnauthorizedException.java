package com.campx.academic.resource.exception;

/**
 * Thrown when credentials or API key are missing, invalid, or expired (HTTP 401).
 */
public class ResourceUnauthorizedException extends ResourceException {

    public ResourceUnauthorizedException(String message) {
        super(401, "ACD_RESOURCE_UNAUTHORIZED", message);
    }
}
