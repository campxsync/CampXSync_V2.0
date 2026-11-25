package com.campx.iadm.governance.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-05 iadm05.* keys (US-023).
 * Plain language: which persona may hold which key. Runtime still uses iam.has_permission;
 * this matrix drives unit denial proofs. No wildcard. View ≠ export/approve.
 */
public final class GovernancePermissionMatrix {

    public static final String[] NEW_KEYS = {
            "iadm05.classification.view",
            "iadm05.classification.propose",
            "iadm05.retention.view",
            "iadm05.retention.propose",
            "iadm05.privacy.view",
            "iadm05.privacy.propose",
            "iadm05.policy.approve",
            "iadm05.policy.publish",
            "iadm05.policy.history",
            "iadm05.export.request",
            "iadm05.export.decide",
            "iadm05.erasure.request",
            "iadm05.erasure.decide",
            "iadm05.erasure.close",
            "iadm05.sharing.view",
            "iadm05.sharing.propose",
            "iadm05.sharing.decide",
            "iadm05.sharing.lifecycle",
            "iadm05.requests.operate",
            "iadm05.adherence.view",
            "iadm05.college.view",
            "iadm05.audit.view",
            "iadm05.domain.contract"
    };

    private static final Map<String, Set<String>> BY_ROLE;

    static {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        // R01 Super Admin — full manage (platform path); listed for offline proofs
        m.put("R01", setOf(NEW_KEYS));
        // R02 System Admin — propose/submit/operate; never approve/publish/decide/close
        m.put("R02", setOf(
                "iadm05.classification.view",
                "iadm05.classification.propose",
                "iadm05.retention.view",
                "iadm05.retention.propose",
                "iadm05.privacy.view",
                "iadm05.privacy.propose",
                "iadm05.policy.history",
                "iadm05.export.request",
                "iadm05.erasure.request",
                "iadm05.erasure.decide",
                "iadm05.sharing.view",
                "iadm05.sharing.propose",
                "iadm05.requests.operate",
                "iadm05.audit.view"));
        // R03 Institution Admin — view-only adherence (G-16)
        m.put("R03", setOf(
                "iadm05.adherence.view",
                "iadm05.classification.view",
                "iadm05.retention.view",
                "iadm05.privacy.view",
                "iadm05.sharing.view",
                "iadm05.policy.history"));
        // R04 College Admin — view + informational entry points (G-02)
        m.put("R04", setOf(
                "iadm05.college.view",
                "iadm05.classification.view",
                "iadm05.retention.view",
                "iadm05.privacy.view"));
        // R28 Auditor — read-only
        m.put("R28", setOf(
                "iadm05.classification.view",
                "iadm05.retention.view",
                "iadm05.privacy.view",
                "iadm05.sharing.view",
                "iadm05.policy.history",
                "iadm05.audit.view"));
        // Machine / DEMO simulator
        m.put("SERVICE", setOf("iadm05.domain.contract"));
        // R05–R39: empty — explicit deny
        m.put("R05", Collections.<String>emptySet());
        m.put("R10", Collections.<String>emptySet());
        m.put("R25", Collections.<String>emptySet());
        m.put("R31", Collections.<String>emptySet());
        BY_ROLE = Collections.unmodifiableMap(m);
    }

    private GovernancePermissionMatrix() {
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

    public static Set<String> keysFor(String persona) {
        Set<String> keys = BY_ROLE.get(normalizePersona(persona));
        if (keys == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(keys);
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
        return p;
    }

    private static Set<String> setOf(String... keys) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(keys)));
    }
}
