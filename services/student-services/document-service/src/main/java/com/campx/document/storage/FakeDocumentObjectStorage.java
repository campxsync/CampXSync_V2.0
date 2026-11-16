package com.campx.document.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for STM-06-EN-002 unit tests (no live Supabase).
 */
public class FakeDocumentObjectStorage implements DocumentObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<String, byte[]>();
    private final String bucket;
    private final boolean configured;
    private volatile boolean failNext;
    private volatile boolean failNextDelete;
    private volatile boolean failNextList;

    public FakeDocumentObjectStorage() {
        this(DocumentStorageConfig.DEFAULT_BUCKET, true);
    }

    public FakeDocumentObjectStorage(String bucket, boolean configured) {
        this.bucket = bucket == null ? DocumentStorageConfig.DEFAULT_BUCKET : bucket;
        this.configured = configured;
    }

    public void failNextOperation() {
        this.failNext = true;
    }

    public void failNextDelete() {
        this.failNextDelete = true;
    }

    public void failNextList() {
        this.failNextList = true;
    }

    public void clear() {
        objects.clear();
        failNext = false;
        failNextDelete = false;
        failNextList = false;
    }

    public int objectCount() {
        return objects.size();
    }

    public boolean contains(String bucketName, String objectPath) {
        return objects.containsKey(key(bucketName, objectPath));
    }

    @Override
    public void putObject(String bucketName, String objectPath, byte[] bytes, String contentType)
            throws DocumentStorageException {
        if (failNext) {
            failNext = false;
            throw new DocumentStorageException("STORAGE_UNAVAILABLE", "Fake storage forced failure on put");
        }
        if (objectPath == null || objectPath.trim().isEmpty()) {
            throw new DocumentStorageException("INVALID_OBJECT_PATH", "Object path is required");
        }
        if (bytes == null) {
            throw new DocumentStorageException("EMPTY_OBJECT", "Object bytes are required");
        }
        objects.put(key(bucketName, objectPath), copy(bytes));
    }

    @Override
    public byte[] getObject(String bucketName, String objectPath) throws DocumentStorageException {
        if (failNext) {
            failNext = false;
            throw new DocumentStorageException("STORAGE_UNAVAILABLE", "Fake storage forced failure on get");
        }
        byte[] found = objects.get(key(bucketName, objectPath));
        if (found == null) {
            throw new DocumentStorageException("OBJECT_NOT_FOUND", "Object not found in fake storage");
        }
        return copy(found);
    }

    @Override
    public List<String> listObjectPaths(String bucketName, String prefix) throws DocumentStorageException {
        if (failNextList) {
            failNextList = false;
            throw new DocumentStorageException("STORAGE_UNAVAILABLE", "Fake storage forced failure on list");
        }
        String p = prefix == null ? "" : prefix.trim();
        String bucketKeyPrefix = (bucketName == null ? "" : bucketName) + "::";
        List<String> out = new ArrayList<String>();
        for (String fullKey : objects.keySet()) {
            if (!fullKey.startsWith(bucketKeyPrefix)) {
                continue;
            }
            String path = fullKey.substring(bucketKeyPrefix.length());
            if (p.isEmpty() || path.equals(p) || path.startsWith(p)) {
                out.add(path);
            }
        }
        Collections.sort(out);
        return out;
    }

    @Override
    public void deleteObject(String bucketName, String objectPath) throws DocumentStorageException {
        if (failNextDelete) {
            failNextDelete = false;
            throw new DocumentStorageException("STORAGE_UNAVAILABLE", "Fake storage forced failure on delete");
        }
        if (objectPath == null || objectPath.trim().isEmpty()) {
            throw new DocumentStorageException("INVALID_OBJECT_PATH", "Object path is required");
        }
        // Idempotent: missing key is success
        objects.remove(key(bucketName, objectPath.trim()));
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    private static String key(String bucketName, String objectPath) {
        return (bucketName == null ? "" : bucketName) + "::" + objectPath;
    }

    private static byte[] copy(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        System.arraycopy(bytes, 0, out, 0, bytes.length);
        return out;
    }
}
