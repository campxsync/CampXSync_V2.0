package com.campx.iadm.analytics.client;

import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.service.AnalyticsOutageFlags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IADM-05 domain contract client (IADM-06-US-021).
 * Plain language: IADM-06 is the first real consumer — resolve exactly one published policy
 * before releasing a dataset or export; otherwise withhold (fail closed).
 */
public class Iadm05PolicyClient {

    public static final String DATA_HANDLING_TYPE = "RETENTION";
    public static final String DATA_HANDLING_DOMAIN = "ANALYTICS";
    /**
     * Finding (no IADM-05 code change): IADM-05 has no propose/publish path for policy_type=EXPORT
     * (EXPORT is a governance-request type). Export gate therefore resolves the same analytics-domain
     * RETENTION published version as data-handling.
     */
    public static final String EXPORT_POLICY_TYPE = "RETENTION";
    public static final String EXPORT_POLICY_DOMAIN = "ANALYTICS";

    private final HttpJsonClient http;

    public Iadm05PolicyClient(String baseUrl) {
        this.http = new HttpJsonClient(baseUrl, 3000, 8000);
    }

    public Map<String, Object> resolveDataHandling(Map<String, String> forwardHeaders) {
        return resolve(DATA_HANDLING_TYPE, DATA_HANDLING_DOMAIN, forwardHeaders);
    }

    public Map<String, Object> resolveExportPolicy(Map<String, String> forwardHeaders) {
        return resolve(EXPORT_POLICY_TYPE, EXPORT_POLICY_DOMAIN, forwardHeaders);
    }

    public Map<String, Object> resolve(String policyType, String domainCode, Map<String, String> forwardHeaders) {
        if (AnalyticsOutageFlags.isIadm05Disabled()) {
            throw new InstituteAdminException(503, "IADM06_POLICY_UNAVAILABLE",
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
            if (ex.getStatus() == 404) {
                throw new InstituteAdminException(403, "IADM06_POLICY_UNRESOLVED",
                        "No single published policy version resolved; dataset withheld.");
            }
            if (ex.getStatus() == 409) {
                throw new InstituteAdminException(403, "IADM06_POLICY_UNRESOLVED",
                        "Conflicting published policy versions; dataset withheld.");
            }
            if ("IADM06_POLICY_RESOLVE".equals(ex.getErrorCode())) {
                throw new InstituteAdminException(403, "IADM06_POLICY_UNRESOLVED",
                        "Policy could not be resolved; dataset withheld.");
            }
            throw ex;
        }
    }

    private static String url(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Tiny JSON field extractor — avoids a new JSON library dependency. */
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
