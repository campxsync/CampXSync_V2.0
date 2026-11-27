package com.campx.iadm.access.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Role x permission matrix for IADM-03.
 * Supports both additive iadm03.* keys (US-018) and legacy V1 iam.* keys for backward-compatible unit tests.
 * Permission keys only — never authorize by role name alone; no wildcard; no apply-grant key.
 */
public final class AccessPermissionMatrix {

    public static final String[] IADM03_KEYS = {
            "iadm03.register.view",
            "iadm03.matrix.view",
            "iadm03.holders.view",
            "iadm03.grant.request.submit",
            "iadm03.grant.review",
            "iadm03.grant.approve",
            "iadm03.review.manage",
            "iadm03.delegation.manage",
            "iadm03.registration.submit",
            "iadm03.registration.approve",
            "iadm03.handover.view",
            "iadm03.evidence.view"
    };

    public static final String[] NEW_KEYS = {
            "iam.catalog.roles.view",
            "iam.catalog.permissions.view",
            "iam.access.overview.view",
            "iam.access.subject.view",
            "iam.profile.invite",
            "iam.profile.view",
            "iam.profile.status",
            "iam.grant.assign",
            "iam.grant.revoke",
            "iam.grant.r03.decide",
            "iam.request.submit",
            "iam.request.decide",
            "iam.delegation.create",
            "iam.delegation.revoke",
            "iam.catalog.change.propose",
            "iam.catalog.change.approve",
            "iam.review.manage",
            "iam.audit.view",
            "iam.governance.read",
            "iam.identity.technical.assign"
    };

    public static final String[] PERSONAS = {
            "R01", "R02", "R03", "R04", "R28", "R27"
    };

    private static final Map<String, Set<String>> IADM03_BY_ROLE;
    private static final Map<String, Set<String>> BY_ROLE;

    static {
        Map<String, Set<String>> m03 = new HashMap<String, Set<String>>();
        m03.put("R01", setOf(IADM03_KEYS));
        m03.put("R02", setOf(
                "iadm03.register.view",
                "iadm03.matrix.view",
                "iadm03.holders.view",
                "iadm03.grant.request.submit",
                "iadm03.grant.review",
                "iadm03.review.manage",
                "iadm03.registration.submit",
                "iadm03.handover.view",
                "iadm03.evidence.view"));
        m03.put("R03", setOf(
                "iadm03.holders.view",
                "iadm03.grant.request.submit",
                "iadm03.delegation.manage",
                "iadm03.handover.view"));
        m03.put("R04", setOf("iadm03.grant.request.submit"));
        m03.put("R28", setOf(
                "iadm03.register.view",
                "iadm03.matrix.view",
                "iadm03.holders.view",
                "iadm03.handover.view",
                "iadm03.evidence.view"));
        m03.put("R27", Collections.<String>emptySet());
        IADM03_BY_ROLE = Collections.unmodifiableMap(m03);

        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        m.put("R01", setOf(NEW_KEYS));
        m.put("R02", setOf(
                "iam.catalog.roles.view",
                "iam.catalog.permissions.view",
                "iam.access.overview.view",
                "iam.profile.view",
                "iam.identity.technical.assign",
                "iam.governance.read",
                "iam.audit.view"));
        m.put("R03", setOf(
                "iam.catalog.roles.view",
                "iam.catalog.permissions.view",
                "iam.access.overview.view",
                "iam.access.subject.view",
                "iam.profile.invite",
                "iam.profile.view",
                "iam.profile.status",
                "iam.grant.assign",
                "iam.grant.revoke",
                "iam.grant.r03.decide",
                "iam.request.submit",
                "iam.request.decide",
                "iam.delegation.create",
                "iam.delegation.revoke",
                "iam.review.manage",
                "iam.audit.view"));
        m.put("R04", setOf("iam.request.submit"));
        m.put("R28", setOf(
                "iam.catalog.roles.view",
                "iam.catalog.permissions.view",
                "iam.access.subject.view",
                "iam.profile.view",
                "iam.audit.view"));
        m.put("R27", Collections.<String>emptySet());
        BY_ROLE = Collections.unmodifiableMap(m);
    }

    private AccessPermissionMatrix() {
    }

    public static boolean allowsIadm03(String persona, String permissionKey) {
        if (persona == null || permissionKey == null) {
            return false;
        }
        String role = normalizePersona(persona);
        Set<String> keys = IADM03_BY_ROLE.get(role);
        return keys != null && keys.contains(permissionKey.trim());
    }

    public static boolean allows(String persona, String permissionKey) {
        if (persona == null || permissionKey == null) {
            return false;
        }
        if (permissionKey.trim().startsWith("iadm03.")) {
            return allowsIadm03(persona, permissionKey);
        }
        String role = normalizePersona(persona);
        Set<String> keys = BY_ROLE.get(role);
        if (keys == null) {
            return false;
        }
        return keys.contains(permissionKey.trim());
    }

    public static String normalizePersona(String persona) {
        if (persona == null) {
            return "UNKNOWN";
        }
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
        if ("API_CLIENT".equals(p) || "R27".equals(p) || "SERVICE".equals(p)) {
            return "R27";
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
