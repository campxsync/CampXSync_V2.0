package com.campx.iadm.security.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-07 iadm07.* keys (US-023).
 * Plain language: which persona may hold which key. Runtime still uses iam.has_permission.
 */
public final class SecurityPermissionMatrix {

    public static final String[] ALL_KEYS = new String[]{
            "iadm07.posture.view",
            "iadm07.register.read",
            "iadm07.register.write",
            "iadm07.standard.propose",
            "iadm07.standard.approve",
            "iadm07.exception.request",
            "iadm07.exception.decide",
            "iadm07.signal.intake",
            "iadm07.report.view",
            "iadm07.audit.view",
            "iadm07.summary.view"
    };

    private static final Map<String, Set<String>> BY_PERSONA = build();

    private SecurityPermissionMatrix() {
    }

    private static Map<String, Set<String>> build() {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        // Super Admin (R01) manages all human capabilities; intake is service-caller only
        m.put("R01", setOf(
                "iadm07.posture.view",
                "iadm07.register.read",
                "iadm07.register.write",
                "iadm07.standard.propose",
                "iadm07.standard.approve",
                "iadm07.exception.request",
                "iadm07.exception.decide",
                "iadm07.report.view",
                "iadm07.audit.view",
                "iadm07.summary.view"));
        m.put("R02", setOf(
                "iadm07.posture.view",
                "iadm07.register.read",
                "iadm07.register.write",
                "iadm07.standard.propose",
                "iadm07.exception.request",
                "iadm07.report.view",
                "iadm07.audit.view",
                "iadm07.summary.view"));
        m.put("R03", setOf(
                "iadm07.posture.view",
                "iadm07.register.read",
                "iadm07.register.write",
                "iadm07.exception.request",
                "iadm07.report.view",
                "iadm07.audit.view"));
        m.put("R04", setOf("iadm07.summary.view"));
        m.put("R28", setOf(
                "iadm07.posture.view",
                "iadm07.register.read",
                "iadm07.report.view",
                "iadm07.audit.view"));
        m.put("SERVICE", setOf("iadm07.signal.intake"));
        m.put("R05", Collections.<String>emptySet());
        m.put("R10", Collections.<String>emptySet());
        m.put("R25", Collections.<String>emptySet());
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
        String role = normalizePersona(persona);
        Set<String> keys = BY_PERSONA.get(role);
        if (keys == null) {
            return false;
        }
        return keys.contains(permissionKey.trim());
    }

    private static String normalizePersona(String persona) {
        String p = persona.trim().toUpperCase(Locale.ROOT);
        if (p.startsWith("R") && p.length() <= 3) {
            return p;
        }
        if ("SUPER_ADMIN".equals(p) || "PLATFORM_ADMIN".equals(p)) {
            return "R01";
        }
        if ("SYSTEM_ADMIN".equals(p)) {
            return "R02";
        }
        if ("INSTITUTION_ADMIN".equals(p)) {
            return "R03";
        }
        if ("COLLEGE_ADMIN".equals(p)) {
            return "R04";
        }
        if ("AUDITOR".equals(p)) {
            return "R28";
        }
        if ("SERVICE".equals(p)) {
            return "SERVICE";
        }
        return p;
    }
}