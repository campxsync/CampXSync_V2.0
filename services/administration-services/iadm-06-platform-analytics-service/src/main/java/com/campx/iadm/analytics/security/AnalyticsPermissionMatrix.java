package com.campx.iadm.analytics.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-06 iadm06.* keys (US-018).
 * Plain language: which persona may hold which key. Runtime still uses iam.has_permission.
 */
public final class AnalyticsPermissionMatrix {

    public static final String[] ALL_KEYS = new String[]{
            "iadm06.platform.view",
            "iadm06.institute.view",
            "iadm06.entitlement.view",
            "iadm06.export.request",
            "iadm06.export.view",
            "iadm06.catalogue.view",
            "iadm06.catalogue.manage"
    };

    private static final Map<String, Set<String>> BY_PERSONA = build();

    private AnalyticsPermissionMatrix() {
    }

    private static Map<String, Set<String>> build() {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        // Super Admin (R01) — platform view and export; manage reserved not granted at runtime in V1
        m.put("R01", setOf(
                "iadm06.platform.view",
                "iadm06.institute.view",
                "iadm06.entitlement.view",
                "iadm06.export.request",
                "iadm06.export.view",
                "iadm06.catalogue.view"));
        // System Admin (R02) — view and export; no manage
        m.put("R02", setOf(
                "iadm06.platform.view",
                "iadm06.institute.view",
                "iadm06.entitlement.view",
                "iadm06.export.request",
                "iadm06.export.view",
                "iadm06.catalogue.view"));
        // Institution Admin (R03) — own institute only
        m.put("R03", setOf(
                "iadm06.institute.view",
                "iadm06.entitlement.view",
                "iadm06.export.request",
                "iadm06.export.view",
                "iadm06.catalogue.view"));
        // College Admin (R04) — nothing
        m.put("R04", Collections.<String>emptySet());
        // Auditor (R28) — view only, never export
        m.put("R28", setOf(
                "iadm06.platform.view",
                "iadm06.institute.view",
                "iadm06.entitlement.view",
                "iadm06.catalogue.view"));
        return m;
    }

    private static Set<String> setOf(String... keys) {
        Set<String> s = new HashSet<String>();
        for (int i = 0; i < keys.length; i++) {
            s.add(keys[i]);
        }
        return Collections.unmodifiableSet(s);
    }

    public static boolean allows(String persona, String permissionKey) {
        if (persona == null || permissionKey == null) {
            return false;
        }
        // Reserved manage key: never allowed in V1 offline matrix
        if ("iadm06.catalogue.manage".equals(permissionKey.trim())) {
            return false;
        }
        Set<String> keys = BY_PERSONA.get(persona.trim().toUpperCase());
        if (keys == null) {
            return false;
        }
        return keys.contains(permissionKey.trim());
    }
}
