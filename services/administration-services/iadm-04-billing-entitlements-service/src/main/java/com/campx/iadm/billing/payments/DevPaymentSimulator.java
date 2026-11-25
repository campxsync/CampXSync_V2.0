package com.campx.iadm.billing.payments;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Clearly labelled DEV payment simulator (US-026 / D3).
 * No real provider, credentials, or outbound network calls.
 */
public final class DevPaymentSimulator implements PaymentProviderAdapter {

    public static final String PROVIDER_NAME = "DEV_SIMULATOR";
    /** DEV-only default; override with env IADM04_PAYMENT_SIMULATOR_SECRET. Never use in production. */
    public static final String DEV_DEFAULT_SECRET = "iadm04-dev-simulator-secret-not-for-prod";

    private final String secret;

    public DevPaymentSimulator() {
        String env = System.getenv("IADM04_PAYMENT_SIMULATOR_SECRET");
        this.secret = (env == null || env.trim().isEmpty()) ? DEV_DEFAULT_SECRET : env.trim();
    }

    public DevPaymentSimulator(String secret) {
        this.secret = secret == null || secret.trim().isEmpty() ? DEV_DEFAULT_SECRET : secret.trim();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        String outcome = normalizeOutcome(request.simulatorOutcome);
        String providerRef = "SIM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);

        if ("FAILURE".equals(outcome)) {
            return new InitiateResult(providerRef, "FAILURE",
                    "DEV simulator produced failure outcome (no network).", null);
        }
        if ("DUPLICATE".equals(outcome)) {
            return new InitiateResult(providerRef, "DUPLICATE",
                    "DEV simulator duplicate initiation signal.", null);
        }
        if ("INVALID_SIGNATURE".equals(outcome)) {
            Map<String, Object> hint = new LinkedHashMap<String, Object>();
            hint.put("note", "Caller should post callback with a bad signature to exercise rejection.");
            hint.put("providerRef", providerRef);
            return new InitiateResult(providerRef, "PENDING",
                    "DEV simulator ready; expect invalid-signature callback test.", hint);
        }

        // PENDING or SUCCESS — initiation is never settlement; SUCCESS only prepares a success callback hint
        String eventId = "evt-" + UUID.randomUUID().toString();
        CallbackDraft draft = new CallbackDraft(
                eventId,
                request.intentId,
                providerRef,
                "SUCCESS".equals(outcome) ? "SUCCESS" : "PENDING",
                request.amountMinorUnits,
                request.currencyCode
        );
        SignedCallbackEnvelope envelope = buildSignedCallback(draft);
        Map<String, Object> hint = new LinkedHashMap<String, Object>();
        hint.put("provider", PROVIDER_NAME);
        hint.put("eventId", eventId);
        hint.put("timestamp", envelope.timestampEpochSeconds);
        hint.put("signature", envelope.signatureHex);
        hint.put("body", envelope.bodyMap);
        hint.put("bodyJson", envelope.bodyJson);
        hint.put("labelled", "DEV simulator — not a real payment provider");
        return new InitiateResult(providerRef, "PENDING",
                "DEV simulator accepted initiation; settlement requires signed callback.", hint);
    }

    @Override
    public SignedCallbackEnvelope buildSignedCallback(CallbackDraft draft) {
        long ts = System.currentTimeMillis() / 1000L;
        String timestamp = String.valueOf(ts);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("eventId", draft.eventId);
        body.put("intentId", draft.intentId);
        body.put("providerRef", draft.providerRef);
        body.put("settlementStatus", draft.settlementStatus);
        body.put("amountMinorUnits", Long.valueOf(draft.amountMinorUnits));
        body.put("currencyCode", draft.currencyCode);
        body.put("provider", PROVIDER_NAME);
        String bodyJson = canonicalJson(body);
        String signature = sign(timestamp, bodyJson);
        return new SignedCallbackEnvelope(timestamp, signature, bodyJson, body);
    }

    @Override
    public boolean verifySignature(String timestampEpochSeconds, String body, String signatureHex) {
        if (timestampEpochSeconds == null || body == null || signatureHex == null) {
            return false;
        }
        String expected = sign(timestampEpochSeconds.trim(), body);
        return constantTimeEquals(expected, signatureHex.trim().toLowerCase(Locale.ROOT));
    }

    public String sign(String timestampEpochSeconds, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((timestampEpochSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("DEV simulator HMAC failed", ex);
        }
    }

    public static String canonicalJson(Map<String, Object> body) {
        // Stable key order for signature — LinkedHashMap insertion order.
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String normalizeOutcome(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "PENDING";
        }
        String o = raw.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(o) || "FAILURE".equals(o) || "DUPLICATE".equals(o)
                || "INVALID_SIGNATURE".equals(o) || "PENDING".equals(o)) {
            return o;
        }
        return "PENDING";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", Integer.valueOf(b & 0xff)));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
