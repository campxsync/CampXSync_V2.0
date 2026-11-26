package com.campx.iadm.ops.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-08 iadm08.* keys (US-017).
 * Plain language: which persona may hold which key. Runtime still uses iam.has_permission.
 */
public final class OpsPermissionMatrix {

    public static final String KEY_AUDIT_PLATFORM = "iadm08.audit.platform.view";
    public static final String KEY_AUDIT_INSTITUTE = "iadm08.audit.institute.view";
    public static final String KEY_HEALTH = "iadm08.health.view";
    public static final String KEY_ALERTS = "iadm08.alerts.view";
    public static final String KEY_MANAGE = "iadm08.manage";

    public static final String[] ALL_KEYS = new String[]{
            KEY_AUDIT_PLATFORM,
            KEY_AUDIT_INSTITUTE,
            KEY_HEALTH,
            KEY_ALERTS,
            KEY_MANAGE
    };

    private static final Map<String, Set<String>> BY_PERSONA = build();

    private OpsPermissionMatrix() {
    }

    private static Map<String, Set<String>> build() {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        // Super Admin (R01) — view/investigate; manage reserved not granted
        m.put("R01", setOf(KEY_AUDIT_PLATFORM, KEY_AUDIT_INSTITUTE, KEY_HEALTH, KEY_ALERTS));
        // System Admin (R02) — same view/investigate
        m.put("R02", setOf(KEY_AUDIT_PLATFORM, KEY_AUDIT_INSTITUTE, KEY_HEALTH, KEY_ALERTS));
        // Institution Admin (R03) — own-institute audit only; no health
        m.put("R03", setOf(KEY_AUDIT_INSTITUTE, KEY_ALERTS));
        // College Admin (R04) — nothing
        m.put("R04", Collections.<String>emptySet());
        // Auditor (R28) — read-only platform-wide; no export; no manage
        m.put("R28", setOf(KEY_AUDIT_PLATFORM, KEY_AUDIT_INSTITUTE, KEY_HEALTH, KEY_ALERTS));
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
        if (KEY_MANAGE.equals(permissionKey.trim())) {
            return false;
        }
        Set<String> keys = BY_PERSONA.get(persona.trim().toUpperCase());
        if (keys == null) {
            return false;
        }
        return keys.contains(permissionKey.trim());
    }
}
