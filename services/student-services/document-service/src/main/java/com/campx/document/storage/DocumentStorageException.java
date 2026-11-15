package com.campx.document.storage;

public class DocumentStorageException extends Exception {

    private final String code;

    public DocumentStorageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentStorageException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
