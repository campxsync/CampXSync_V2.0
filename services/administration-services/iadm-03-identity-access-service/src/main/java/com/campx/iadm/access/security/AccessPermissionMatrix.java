package com.campx.iadm.access.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-03 iam.* keys (owner-approved S0 D3).
 * Permission keys only — never authorize by role name at the endpoint.
 * The dormant adm03.* family is intentionally absent.
 */
public final class AccessPermissionMatrix {

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

    private static final Map<String, Set<String>> BY_ROLE;

    static {
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
        // R04 request-only in IADM-03
        m.put("R04", setOf("iam.request.submit"));
        // R28 Auditor read-only
        m.put("R28", setOf(
                "iam.catalog.roles.view",
                "iam.catalog.permissions.view",
                "iam.access.subject.view",
                "iam.profile.view",
                "iam.audit.view"));
        // R27 API Client — denial tests only; no IADM-03 keys
        m.put("R27", Collections.<String>emptySet());
        BY_ROLE = Collections.unmodifiableMap(m);
    }

    private AccessPermissionMatrix() {
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
