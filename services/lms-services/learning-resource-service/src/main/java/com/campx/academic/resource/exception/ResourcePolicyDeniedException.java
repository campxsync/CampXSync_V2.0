package com.campx.academic.resource.exception;

/**
 * Thrown when an actor's access request is denied by the Access Policy Engine (HTTP 403).
 */
public class ResourcePolicyDeniedException extends ResourceException {

    public ResourcePolicyDeniedException(String message) {
        super(403, "ACD_RESOURCE_POLICY_DENIED", message);
    }
}
