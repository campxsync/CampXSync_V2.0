package com.campx.iadm.billing.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline role × permission matrix for IADM-04 iadm04.* keys (owner-approved).
 * Permission keys only — never authorize by role name at the endpoint.
 */
public final class BillingPermissionMatrix {

    public static final String[] NEW_KEYS = {
            "iadm04.plans.view",
            "iadm04.plans.manage",
            "iadm04.entitlements.map",
            "iadm04.subscriptions.view",
            "iadm04.subscriptions.view.own",
            "iadm04.plan_change.request",
            "iadm04.plan_change.decide",
            "iadm04.subscriptions.attach",
            "iadm04.entitlements.check",
            "iadm04.invoices.manage",
            "iadm04.invoices.view.own",
            "iadm04.payments.offline.record",
            "iadm04.payments.offline.reconcile",
            "iadm04.payments.online.initiate",
            "iadm04.payments.callback",
            "iadm04.payments.callback.retry",
            "iadm04.usage.record",
            "iadm04.reports.view",
            "iadm04.audit.view",
            "iadm04.jobs.run"
    };

    private static final Map<String, Set<String>> BY_ROLE;

    static {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        m.put("R01", setOf(
                "iadm04.plans.view",
                "iadm04.plans.manage",
                "iadm04.entitlements.map",
                "iadm04.subscriptions.view",
                "iadm04.plan_change.decide",
                "iadm04.subscriptions.attach",
                "iadm04.entitlements.check",
                "iadm04.invoices.manage",
                "iadm04.payments.offline.record",
                "iadm04.payments.offline.reconcile",
                "iadm04.payments.online.initiate",
                "iadm04.payments.callback",
                "iadm04.usage.record",
                "iadm04.reports.view",
                "iadm04.audit.view",
                "iadm04.jobs.run"));
        m.put("R02", setOf(
                "iadm04.plans.view",
                "iadm04.subscriptions.view",
                "iadm04.entitlements.check",
                "iadm04.payments.callback.retry",
                "iadm04.reports.view",
                "iadm04.audit.view"));
        m.put("R03", setOf(
                "iadm04.subscriptions.view.own",
                "iadm04.plan_change.request",
                "iadm04.invoices.view.own",
                "iadm04.reports.view"));
        m.put("R28", setOf(
                "iadm04.plans.view",
                "iadm04.subscriptions.view",
                "iadm04.reports.view",
                "iadm04.audit.view"));
        m.put("R04", Collections.<String>emptySet());
        m.put("SERVICE", setOf(
                "iadm04.subscriptions.attach",
                "iadm04.entitlements.check",
                "iadm04.usage.record",
                "iadm04.payments.callback",
                "iadm04.jobs.run"));
        BY_ROLE = Collections.unmodifiableMap(m);
    }

    private BillingPermissionMatrix() {
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
        if ("SERVICE".equals(p) || "TECHNICAL".equals(p) || p.startsWith("SVC-")) {
            return "SERVICE";
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
