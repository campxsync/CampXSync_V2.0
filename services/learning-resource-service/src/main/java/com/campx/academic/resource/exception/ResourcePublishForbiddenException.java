package com.campx.academic.resource.exception;

/**
 * Thrown when an unauthorized user attempts to publish a resource or required workflow approvals are missing (HTTP 403).
 */
public class ResourcePublishForbiddenException extends ResourceException {

    public ResourcePublishForbiddenException(String message) {
        super(403, "ACD_RESOURCE_PUBLISH_FORBIDDEN", message);
    }
}
