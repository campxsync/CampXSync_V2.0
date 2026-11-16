package com.campx.document.storage;

import java.util.List;

/**
 * STM-06-EN-002 object-byte store. Relational tables keep metadata and object path only.
 */
public interface DocumentObjectStorage {

    void putObject(String bucket, String objectPath, byte[] bytes, String contentType) throws DocumentStorageException;

    byte[] getObject(String bucket, String objectPath) throws DocumentStorageException;

    /**
     * Lists object paths under {@code prefix} (inclusive of nested keys).
     * Paths are relative to the bucket root (e.g. {@code tenants/{tenantId}/students/...}).
     */
    List<String> listObjectPaths(String bucket, String prefix) throws DocumentStorageException;

    /**
     * Deletes an object. Idempotent: missing object is success.
     */
    void deleteObject(String bucket, String objectPath) throws DocumentStorageException;

    boolean isConfigured();

    String getDefaultBucket();
}
