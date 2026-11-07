package com.campx.lifecycle;

/**
 * Business or authorization failure from the lifecycle service.
 * The HTTP status and stable code are safe to return to the caller. The message does not include secrets.
 */
public class LifecycleException extends RuntimeException {

    private final int status;
    private final String code;

    public LifecycleException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
