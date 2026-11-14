package com.campx.document.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HMAC-SHA256 signed download tokens for STM-06-EN-003.
 * Token format: {@code base64url(documentId|tenantId|userId|expEpochSec)}.hexHmac
 */
public class SignedDownloadTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private final byte[] secret;

    public SignedDownloadTokenService(String secret) {
        String s = (secret == null || secret.trim().isEmpty())
                ? "campx-dev-document-signed-url-secret"
                : secret.trim();
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID documentId, UUID tenantId, UUID userId, long expiresAtEpochSec) {
        String payload = documentId + "|" + tenantId + "|" + userId + "|" + expiresAtEpochSec;
        String encoded = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + hmacHex(encoded);
    }

    public ParsedToken parseAndVerify(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("SIGNED_URL_TOKEN_REQUIRED");
        }
        String raw = token.trim();
        int dot = raw.lastIndexOf('.');
        if (dot <= 0 || dot == raw.length() - 1) {
            throw new IllegalArgumentException("SIGNED_URL_TOKEN_MALFORMED");
        }
        String encoded = raw.substring(0, dot);
        String signature = raw.substring(dot + 1);
        String expected = hmacHex(encoded);
        if (!constantTimeEquals(expected, signature)) {
            throw new IllegalArgumentException("SIGNED_URL_SIGNATURE_INVALID");
        }
        String payload = new String(base64UrlDecode(encoded), StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("SIGNED_URL_PAYLOAD_MALFORMED");
        }
        ParsedToken parsed = new ParsedToken();
        parsed.documentId = UUID.fromString(parts[0]);
        parsed.tenantId = UUID.fromString(parts[1]);
        parsed.userId = UUID.fromString(parts[2]);
        parsed.expiresAtEpochSec = Long.parseLong(parts[3]);
        return parsed;
    }

    public static class ParsedToken {
        public UUID documentId;
        public UUID tenantId;
        public UUID userId;
        public long expiresAtEpochSec;
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign download token", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String base64Url(byte[] data) {
        String b64 = java.util.Base64.getEncoder().encodeToString(data);
        return b64.replace('+', '-').replace('/', '_').replace("=", "");
    }

    private static byte[] base64UrlDecode(String encoded) {
        String padded = encoded.replace('-', '+').replace('_', '/');
        int mod = padded.length() % 4;
        if (mod > 0) {
            padded = padded + "====".substring(mod);
        }
        return java.util.Base64.getDecoder().decode(padded);
    }
}
