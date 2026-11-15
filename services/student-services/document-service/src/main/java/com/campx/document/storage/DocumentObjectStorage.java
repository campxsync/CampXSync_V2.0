package com.campx.document.storage;

/**
 * STM-06-EN-002 object-byte store. Relational tables keep metadata and object path only.
 */
public interface DocumentObjectStorage {

    void putObject(String bucket, String objectPath, byte[] bytes, String contentType) throws DocumentStorageException;

    byte[] getObject(String bucket, String objectPath) throws DocumentStorageException;

    boolean isConfigured();

    String getDefaultBucket();
}
