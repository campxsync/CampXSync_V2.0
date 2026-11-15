package com.campx.document.storage;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Supabase Storage REST client using JDK {@link HttpURLConnection} only (D11).
 * Never logs the service-role key or object bytes.
 */
public class SupabaseStorageHttpClient implements DocumentObjectStorage {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SupabaseStorageHttpClient.class);
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    private final DocumentStorageConfig config;

    public SupabaseStorageHttpClient(DocumentStorageConfig config) {
        if (config == null || !config.configured) {
            throw new IllegalArgumentException("Supabase storage client requires configured URL and service-role key");
        }
        this.config = config;
    }

    @Override
    public void putObject(String bucket, String objectPath, byte[] bytes, String contentType)
            throws DocumentStorageException {
        String b = bucket == null || bucket.trim().isEmpty() ? config.bucket : bucket.trim();
        String path = requirePath(objectPath);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(trimSlash(config.supabaseUrl) + "/storage/v1/object/" + encodePath(b + "/" + path));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + config.serviceRoleKey);
            conn.setRequestProperty("apikey", config.serviceRoleKey);
            conn.setRequestProperty("Content-Type", contentType == null ? "application/octet-stream" : contentType);
            conn.setRequestProperty("x-upsert", "true");
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                logger.warn("Supabase storage put failed status={} bucket={} pathLen={}",
                        status, b, path.length());
                throw new DocumentStorageException("STORAGE_PUT_FAILED",
                        "Object storage put failed with HTTP " + status);
            }
            logger.info("Supabase storage put ok bucket={} pathLen={} bytes={}", b, path.length(), bytes.length);
        } catch (DocumentStorageException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Supabase storage put error: {}", e.getMessage());
            throw new DocumentStorageException("STORAGE_PUT_FAILED", "Object storage put failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public byte[] getObject(String bucket, String objectPath) throws DocumentStorageException {
        String b = bucket == null || bucket.trim().isEmpty() ? config.bucket : bucket.trim();
        String path = requirePath(objectPath);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(trimSlash(config.supabaseUrl) + "/storage/v1/object/" + encodePath(b + "/" + path));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + config.serviceRoleKey);
            conn.setRequestProperty("apikey", config.serviceRoleKey);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                logger.warn("Supabase storage get failed status={} bucket={} pathLen={}",
                        status, b, path.length());
                throw new DocumentStorageException("STORAGE_GET_FAILED",
                        "Object storage get failed with HTTP " + status);
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
            in.close();
            logger.info("Supabase storage get ok bucket={} pathLen={} bytes={}",
                    b, path.length(), buffer.size());
            return buffer.toByteArray();
        } catch (DocumentStorageException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Supabase storage get error: {}", e.getMessage());
            throw new DocumentStorageException("STORAGE_GET_FAILED", "Object storage get failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String getDefaultBucket() {
        return config.bucket;
    }

    private static String requirePath(String objectPath) throws DocumentStorageException {
        if (objectPath == null || objectPath.trim().isEmpty()) {
            throw new DocumentStorageException("INVALID_OBJECT_PATH", "Object path is required");
        }
        String path = objectPath.trim();
        if (path.contains("..") || path.startsWith("/")) {
            throw new DocumentStorageException("INVALID_OBJECT_PATH", "Object path is invalid");
        }
        return path;
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            try {
                sb.append(java.net.URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
            } catch (java.io.UnsupportedEncodingException e) {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }
}
