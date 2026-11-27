package com.campx.iadm.ops.client;

import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.service.OpsOutageFlags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IADM-05 domain contract client for IADM-08 (US-020 / D15).
 * Plain language: exactly one published RETENTION policy for domain OPERATIONS must resolve
 * before audit/health sensitive data is released; otherwise withhold.
 */
public class Iadm05PolicyClient {

    public static final String DATA_HANDLING_TYPE = "RETENTION";
    public static final String DATA_HANDLING_DOMAIN = "OPERATIONS";

    private final HttpJsonClient http;

    public Iadm05PolicyClient(String baseUrl) {
        this.http = new HttpJsonClient(baseUrl, 3000, 8000);
    }

    public Map<String, Object> resolveOperationsPolicy(Map<String, String> forwardHeaders) {
        return resolve(DATA_HANDLING_TYPE, DATA_HANDLING_DOMAIN, forwardHeaders);
    }

    public Map<String, Object> resolve(String policyType, String domainCode, Map<String, String> forwardHeaders) {
        if (OpsOutageFlags.isIadm05Disabled()) {
            throw new InstituteAdminException(503, "IADM08_POLICY_UNAVAILABLE",
                    "Data policy could not be verified");
        }
        StringBuilder q = new StringBuilder("/domain/resolve-policy?policyType=")
                .append(url(policyType));
        if (domainCode != null && !domainCode.trim().isEmpty()) {
            q.append("&domainCode=").append(url(domainCode.trim()));
        }
        try {
            String raw = http.get(q.toString(), forwardHeaders);
            return parseMinimal(raw);
        } catch (InstituteAdminException ex) {
            throw mapResolveFailure(ex);
        }
    }

    /**
     * Missing (404) or ambiguous/conflicting (409) IADM-05 policy withholds data.
     * Visible for Fix Part A API contract tests.
     */
    public static InstituteAdminException mapResolveFailure(InstituteAdminException ex) {
        if (ex == null) {
            return new InstituteAdminException(403, "IADM08_POLICY_UNRESOLVED",
                    "Policy could not be resolved; data withheld.");
        }
        if (ex.getStatus() == 404) {
            return new InstituteAdminException(403, "IADM08_POLICY_UNRESOLVED",
                    "No single published policy version resolved; data withheld.");
        }
        if (ex.getStatus() == 409) {
            return new InstituteAdminException(403, "IADM08_POLICY_UNRESOLVED",
                    "Conflicting published policy versions; data withheld.");
        }
        if ("IADM08_POLICY_RESOLVE".equals(ex.getErrorCode())) {
            return new InstituteAdminException(403, "IADM08_POLICY_UNRESOLVED",
                    "Policy could not be resolved; data withheld.");
        }
        return ex;
    }

    private static String url(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static Map<String, Object> parseMinimal(String raw) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (raw == null) {
            return out;
        }
        out.put("raw", raw);
        putIfPresent(out, raw, "policyId");
        putIfPresent(out, raw, "code");
        putIfPresent(out, raw, "policyType");
        String vn = extract(raw, "versionNo");
        if (vn != null) {
            try {
                out.put("versionNo", Integer.valueOf(vn.replaceAll("[^0-9]", "")));
            } catch (Exception ignored) {
                out.put("versionNo", vn);
            }
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> out, String raw, String key) {
        String v = extract(raw, key);
        if (v != null) {
            out.put(key, v);
        }
    }

    private static String extract(String raw, String key) {
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = raw.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
            start++;
        }
        if (start >= raw.length()) {
            return null;
        }
        if (raw.charAt(start) == '"') {
            int end = raw.indexOf('"', start + 1);
            if (end < 0) {
                return null;
            }
            return raw.substring(start + 1, end);
        }
        int end = start;
        while (end < raw.length()) {
            char c = raw.charAt(end);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return raw.substring(start, end);
    }
}
