package com.campx.iadm.config.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-02 cfg.* keys (owner D3).
 * R01 Super Admin is expressed as SUPER_ADMIN; tenant roles use catalogue codes.
 */
public final class ConfigPermissionMatrix {

    public static final String[] NEW_KEYS = {
            "cfg.catalog.view",
            "cfg.draft",
            "cfg.approve",
            "cfg.publish",
            "cfg.policy.draft",
            "cfg.consumer.read"
    };

    public static final String[] PERSONAS = {
            "R01", "R02", "R03", "R04", "R28"
    };

    private static final Map<String, Set<String>> BY_ROLE;

    static {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        m.put("R01", setOf(
                "cfg.catalog.view", "cfg.draft", "cfg.approve", "cfg.publish",
                "cfg.policy.draft", "cfg.consumer.read"));
        m.put("R02", setOf("cfg.catalog.view", "cfg.draft"));
        m.put("R03", setOf("cfg.catalog.view"));
        m.put("R04", Collections.<String>emptySet());
        m.put("R28", setOf("cfg.catalog.view"));
        BY_ROLE = Collections.unmodifiableMap(m);
    }

    private ConfigPermissionMatrix() {
    }

    public static boolean allows(String persona, String permissionKey) {
        if (persona == null || permissionKey == null) {
            return false;
        }
        String role = normalizePersona(persona);
        Set<String> keys = BY_ROLE.get(role);
        if (keys == null) {
            return false;
        }
        return keys.contains(permissionKey.trim());
    }

    public static String normalizePersona(String persona) {
        String p = persona.trim().toUpperCase(Locale.ROOT);
        if ("SUPER_ADMIN".equals(p) || "PLATFORM_ADMIN".equals(p) || "R01".equals(p)) {
            return "R01";
        }
        if ("SYSTEM_ADMIN".equals(p) || "R02".equals(p)) {
            return "R02";
        }
        if ("INSTITUTION_ADMIN".equals(p) || "R03".equals(p)) {
            return "R03";
        }
        if ("COLLEGE_ADMIN".equals(p) || "R04".equals(p) || "ADMIN".equals(p)) {
            return "R04";
        }
        if ("AUDITOR".equals(p) || "R28".equals(p)) {
            return "R28";
        }
        return p;
    }

    private static Set<String> setOf(String... keys) {
        Set<String> s = new HashSet<String>();
        for (String k : keys) {
            s.add(k);
        }
        return Collections.unmodifiableSet(s);
    }
}
