package com.campx.guardian;

/**
 * Business or authorization failure from the guardian service.
 * The HTTP status and stable code are safe to return. The message does not include secrets.
 */
public class GuardianException extends RuntimeException {

    private final int status;
    private final String code;

    public GuardianException(int status, String code, String message) {
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
