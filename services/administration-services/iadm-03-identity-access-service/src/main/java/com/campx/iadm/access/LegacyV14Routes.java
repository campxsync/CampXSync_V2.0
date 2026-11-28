package com.campx.iadm.access;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exclusive inventory of restored IADM-03 V1.4 (legacy) routes that can mutate
 * grants or the permission catalogue. Kept until owner decides D-03
 * (apply versus record-only). V2 record-only endpoints must never appear here.
 */
public final class LegacyV14Routes {

    public static final String LABEL = "IADM-03 V1.4 (legacy)";

    /** HTTP method + path suffix under /api/v1/iadm/03 that mutate iam.* grants/catalogue. */
    public static final List<String> GRANT_OR_CATALOGUE_MUTATING = Collections.unmodifiableList(Arrays.asList(
            "POST /grants/appoint-r03",
            "POST /grants/assign",
            "POST /grants/revoke",
            "POST /grants/r03-change/{id}/decide",
            "POST /catalog-changes",
            "POST /catalog-changes/{id}/decide"
    ));

    /** Read-only legacy companion (lists pending catalogue change requests). */
    public static final List<String> LEGACY_READ = Collections.unmodifiableList(Arrays.asList(
            "GET /catalog-changes"
    ));

    private LegacyV14Routes() {
    }

    public static boolean isLegacyGrantOrCatalogueMutation(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String m = method.trim().toUpperCase();
        String p = path;
        if ("POST".equals(m) && p.endsWith("/grants/appoint-r03")) return true;
        if ("POST".equals(m) && p.endsWith("/grants/assign")) return true;
        if ("POST".equals(m) && p.endsWith("/grants/revoke")) return true;
        if ("POST".equals(m) && p.contains("/grants/r03-change/") && p.endsWith("/decide")) return true;
        if ("POST".equals(m) && p.endsWith("/catalog-changes")) return true;
        if ("POST".equals(m) && p.contains("/catalog-changes/") && p.endsWith("/decide")) return true;
        return false;
    }
}
