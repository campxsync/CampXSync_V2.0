package com.campx.gateway.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe client and in-memory cache for Supabase JWKS public keys.
 * <p>
 * Key capabilities:
 * <ul>
 *   <li>Dynamically derives JWKS URL from Supabase project issuer or explicit configuration.</li>
 *   <li>Caches discovered public keys in-memory to prevent per-request network round-trips.</li>
 *   <li>Refreshes JWKS once when an unknown {@code kid} is encountered to support seamless key rotation.</li>
 *   <li>Enforces connect and read timeouts (3s / 5s) and bounded cache capacity.</li>
 *   <li>Fails closed when JWKS endpoint is unreachable or returns invalid keys (never fails open).</li>
 * </ul>
 * </p>
 */
public class JwksClient {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(JwksClient.class);

    public static final long DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    public static final long DEFAULT_REFRESH_COOLDOWN_MS = 10 * 1000L; // 10 seconds minimum cooldown
    public static final int DEFAULT_MAX_CAPACITY = 50;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int READ_TIMEOUT_MS = 5000;

    private final String jwksUrl;
    private final long cacheTtlMs;
    private final long refreshCooldownMs;
    private final int maxCapacity;

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long lastFetchTime = 0L;
    private final Object refreshLock = new Object();

    /**
     * Constructs a JwksClient targeting the specified JWKS endpoint URL with default 10-min TTL and 10-sec cooldown.
     *
     * @param jwksUrl JWKS URL (e.g. {@code https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json})
     */
    public JwksClient(String jwksUrl) {
        this(jwksUrl, DEFAULT_CACHE_TTL_MS, DEFAULT_REFRESH_COOLDOWN_MS, DEFAULT_MAX_CAPACITY);
    }

    /**
     * Constructs a JwksClient with custom TTL and capacity bounds.
     */
    public JwksClient(String jwksUrl, long cacheTtlMs, int maxCapacity) {
        this(jwksUrl, cacheTtlMs, DEFAULT_REFRESH_COOLDOWN_MS, maxCapacity);
    }

    /**
     * Constructs a JwksClient with custom TTL, refresh cooldown, and capacity bounds.
     */
    public JwksClient(String jwksUrl, long cacheTtlMs, long refreshCooldownMs, int maxCapacity) {
        if (jwksUrl == null || jwksUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("JWKS URL must not be null or empty");
        }
        this.jwksUrl = jwksUrl.trim();
        this.cacheTtlMs = cacheTtlMs > 0 ? cacheTtlMs : DEFAULT_CACHE_TTL_MS;
        this.refreshCooldownMs = refreshCooldownMs >= 0 ? refreshCooldownMs : DEFAULT_REFRESH_COOLDOWN_MS;
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : DEFAULT_MAX_CAPACITY;
    }

    /**
     * Derives standard Supabase JWKS endpoint URL from the project issuer.
     *
     * @param issuer Supabase project issuer URL (e.g. {@code https://<project-ref>.supabase.co/auth/v1})
     * @return derived JWKS URL (e.g. {@code https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json})
     */
    public static String deriveJwksUrl(String issuer) {
        if (issuer == null || issuer.trim().isEmpty()) {
            throw new IllegalArgumentException("Issuer must not be null or empty");
        }
        String clean = issuer.trim().replaceAll("/+$", "");
        return clean + "/.well-known/jwks.json";
    }

    /**
     * Resolves the public key corresponding to the given key ID ({@code kid}).
     * <p>
     * 1. If requested kid exists in cache and is within TTL: returns the cached key.
     * 2. If requested kid is absent: acquires the refresh lock.
     * 3. Re-checks the cache after acquiring the lock.
     * 4. If requested kid is still absent AND previous refresh was less than cooldown (10s):
     *    skips remote HTTP request and returns null (fails closed).
     * 5. If cooldown has elapsed: performs exactly one JWKS refresh.
     * 6. Returns the key if present after refresh, otherwise returns null (fails closed).
     * </p>
     *
     * @param kid Key ID from JWT header
     * @return matching {@link PublicKey}, or {@code null} if unresolved
     */
    public PublicKey getPublicKey(String kid) {
        if (kid == null || kid.trim().isEmpty()) {
            return null;
        }
        String cleanKid = kid.trim();
        PublicKey key = keyCache.get(cleanKid);
        long now = System.currentTimeMillis();

        if (key != null && (now - lastFetchTime) < cacheTtlMs) {
            return key;
        }

        // Unknown kid or expired cache: synchronize under refreshLock
        synchronized (refreshLock) {
            key = keyCache.get(cleanKid);
            now = System.currentTimeMillis();
            if (key != null && (now - lastFetchTime) < cacheTtlMs) {
                return key;
            }

            // Enforce minimum refresh cooldown (10 seconds)
            if ((now - lastFetchTime) < refreshCooldownMs) {
                logger.warn("JWKS refresh skipped for unknown kid '{}': cooldown active (last refresh {}ms ago, cooldown {}ms)",
                        cleanKid, (now - lastFetchTime), refreshCooldownMs);
                return null;
            }

            refreshKeys();
            return keyCache.get(cleanKid);
        }
    }

    /**
     * Connects to the JWKS endpoint, parses keys, and populates the in-memory cache.
     */
    public void refreshKeys() {
        HttpURLConnection conn = null;
        try {
            logger.info("Fetching Supabase JWKS from: {}", jwksUrl);
            URL url = new URL(jwksUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                lastFetchTime = System.currentTimeMillis();
                logger.warn("JWKS endpoint returned non-200 HTTP status: {} from {}", status, jwksUrl);
                return;
            }

            String jwksJson = readStream(conn.getInputStream());
            Map<String, PublicKey> parsedKeys = parseJwks(jwksJson);

            if (!parsedKeys.isEmpty()) {
                if (keyCache.size() + parsedKeys.size() > maxCapacity) {
                    keyCache.clear();
                }
                keyCache.putAll(parsedKeys);
                lastFetchTime = System.currentTimeMillis();
                logger.info("Successfully refreshed JWKS cache with {} public keys", parsedKeys.size());
            } else {
                lastFetchTime = System.currentTimeMillis();
                logger.warn("JWKS endpoint returned no valid EC P-256 public keys from {}", jwksUrl);
            }
        } catch (Exception e) {
            lastFetchTime = System.currentTimeMillis();
            logger.warn("Failed to fetch or parse JWKS from {}: {}", jwksUrl, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parses JWKS JSON payload and extracts EC P-256 public keys.
     */
    public static Map<String, PublicKey> parseJwks(String jwksJson) throws Exception {
        Map<String, PublicKey> keys = new HashMap<>();
        if (jwksJson == null || jwksJson.trim().isEmpty()) {
            return keys;
        }

        int keysIndex = indexOfCaseInsensitive(jwksJson, "\"keys\"");
        if (keysIndex < 0) {
            return keys;
        }

        int startBracket = jwksJson.indexOf('[', keysIndex + 6);
        if (startBracket < 0) {
            return keys;
        }

        int endBracket = findMatchingBracket(jwksJson, startBracket, '[', ']');
        if (endBracket < 0) {
            return keys;
        }

        String keysArray = jwksJson.substring(startBracket + 1, endBracket);

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");

        List<String> keyObjects = extractJsonObjects(keysArray);
        for (String keyObj : keyObjects) {
            String kty = extractJsonString(keyObj, "kty");
            String crv = extractJsonString(keyObj, "crv");
            String alg = extractJsonString(keyObj, "alg");
            String kid = extractJsonString(keyObj, "kid");
            String x = extractJsonString(keyObj, "x");
            String y = extractJsonString(keyObj, "y");

            if ("EC".equalsIgnoreCase(kty)
                    && (crv == null || "P-256".equalsIgnoreCase(crv))
                    && (alg == null || "ES256".equalsIgnoreCase(alg))
                    && kid != null && !kid.trim().isEmpty()
                    && x != null && y != null) {
                try {
                    byte[] xBytes = Base64.getUrlDecoder().decode(x.trim());
                    byte[] yBytes = Base64.getUrlDecoder().decode(y.trim());
                    BigInteger xCoord = new BigInteger(1, xBytes);
                    BigInteger yCoord = new BigInteger(1, yBytes);

                    ECPoint point = new ECPoint(xCoord, yCoord);
                    ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecParameters);
                    PublicKey pubKey = keyFactory.generatePublic(keySpec);
                    keys.put(kid.trim(), pubKey);
                } catch (Exception ex) {
                    logger.warn("Skipping malformed EC key with kid '{}' in JWKS: {}", kid, ex.getMessage());
                }
            }
        }
        return keys;
    }

    private static int indexOfCaseInsensitive(String text, String target) {
        if (text == null || target == null) return -1;
        int tLen = target.length();
        int max = text.length() - tLen;
        for (int i = 0; i <= max; i++) {
            if (text.regionMatches(true, i, target, 0, tLen)) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String text, int openPos, char openChar, char closeChar) {
        int depth = 0;
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == openChar) {
                    depth++;
                } else if (c == closeChar) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static List<String> extractJsonObjects(String arrayContent) {
        List<String> list = new ArrayList<>();
        if (arrayContent == null || arrayContent.isEmpty()) {
            return list;
        }
        int i = 0;
        int len = arrayContent.length();
        while (i < len) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                int endObj = findMatchingBracket(arrayContent, i, '{', '}');
                if (endObj > i) {
                    list.add(arrayContent.substring(i, endObj + 1));
                    i = endObj + 1;
                    continue;
                }
            }
            i++;
        }
        return list;
    }

    /**
     * Clears in-memory key cache. Useful for testing and forced eviction.
     */
    public void clearCache() {
        keyCache.clear();
        lastFetchTime = 0L;
    }

    /**
     * Directly inserts a public key into the cache. Useful for unit testing.
     */
    public void putPublicKey(String kid, PublicKey key) {
        if (kid != null && key != null) {
            keyCache.put(kid.trim(), key);
            lastFetchTime = System.currentTimeMillis();
        }
    }

    public int getCacheSize() {
        return keyCache.size();
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public long getLastFetchTime() {
        return lastFetchTime;
    }

    public void setLastFetchTime(long lastFetchTime) {
        this.lastFetchTime = lastFetchTime;
    }

    public long getRefreshCooldownMs() {
        return refreshCooldownMs;
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        Pattern p = Pattern.compile("(?i)\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
