package com.campx.iadm.security.service;

import com.campx.iadm.security.exception.InstituteAdminException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Register lifecycle transition validator (IADM-07-US-026).
 * Plain language: allowed status moves per register type; invalid moves are refused.
 */
public final class RegisterLifecycleEngine {

    public static final String STANDARD = "STANDARD";
    public static final String FINDING = "FINDING";
    public static final String RISK = "RISK";
    public static final String CONTROL_ASSESSMENT = "CONTROL_ASSESSMENT";
    public static final String EXCEPTION = "EXCEPTION";
    public static final String ALERT = "ALERT";
    public static final String SIGNAL = "SIGNAL";

    private static final Map<String, Map<String, Set<String>>> TRANSITIONS = build();

    private RegisterLifecycleEngine() {
    }

    private static Map<String, Map<String, Set<String>>> build() {
        Map<String, Map<String, Set<String>>> all = new HashMap<String, Map<String, Set<String>>>();

        all.put(STANDARD, map(
                pair("DRAFT", "IN_REVIEW"),
                pair("IN_REVIEW", "DRAFT", "APPROVED"),
                pair("APPROVED", "PUBLISHED"),
                pair("PUBLISHED", "SUPERSEDED")));

        all.put(FINDING, map(
                pair("OPEN", "TRIAGED"),
                pair("TRIAGED", "ASSIGNED", "OPEN"),
                pair("ASSIGNED", "REMEDIATION_IN_PROGRESS"),
                pair("REMEDIATION_IN_PROGRESS", "PENDING_VERIFICATION", "ASSIGNED"),
                pair("PENDING_VERIFICATION", "RESOLVED", "REMEDIATION_IN_PROGRESS")));

        all.put(RISK, map(
                pair("IDENTIFIED", "ASSESSED"),
                pair("ASSESSED", "TREATMENT_PLANNED", "MONITORED"),
                pair("TREATMENT_PLANNED", "TREATING"),
                pair("TREATING", "MONITORED", "TREATMENT_PLANNED"),
                pair("MONITORED", "CLOSED", "TREATMENT_PLANNED")));

        all.put(CONTROL_ASSESSMENT, map(
                pair("NOT_ASSESSED", "EVIDENCE_PENDING", "IN_REVIEW"),
                pair("EVIDENCE_PENDING", "IN_REVIEW"),
                pair("IN_REVIEW", "SATISFIED", "GAP", "EVIDENCE_PENDING"),
                pair("GAP", "EVIDENCE_PENDING", "EXCEPTION"),
                pair("EXCEPTION", "IN_REVIEW", "GAP")));

        all.put(EXCEPTION, map(
                pair("REQUESTED", "APPROVED", "REJECTED")));

        all.put(ALERT, map(
                pair("OPEN", "ACKNOWLEDGED"),
                pair("ACKNOWLEDGED", "RESOLVED", "OPEN")));

        all.put(SIGNAL, map(
                pair("NEW", "ACKNOWLEDGED", "INVESTIGATING", "CLOSED"),
                pair("ACKNOWLEDGED", "INVESTIGATING", "CLOSED"),
                pair("INVESTIGATING", "CLOSED")));

        return Collections.unmodifiableMap(all);
    }

    private static Map<String, Set<String>> map(Map.Entry<String, Set<String>>... entries) {
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        for (int i = 0; i < entries.length; i++) {
            m.put(entries[i].getKey(), entries[i].getValue());
        }
        return Collections.unmodifiableMap(m);
    }

    private static Map.Entry<String, Set<String>> pair(String from, String... to) {
        Set<String> targets = new HashSet<String>();
        for (int i = 0; i < to.length; i++) {
            targets.add(to[i]);
        }
        return new java.util.AbstractMap.SimpleEntry<String, Set<String>>(from, Collections.unmodifiableSet(targets));
    }

    public static void assertTransition(String register, String from, String to) {
        if (register == null || from == null || to == null) {
            throw new InstituteAdminException(400, "IADM07_INVALID_TRANSITION",
                    "Register transition requires register type, from status and to status");
        }
        String reg = register.trim().toUpperCase(Locale.ROOT);
        String f = from.trim().toUpperCase(Locale.ROOT);
        String t = to.trim().toUpperCase(Locale.ROOT);
        if (f.equals(t)) {
            return;
        }
        Map<String, Set<String>> byFrom = TRANSITIONS.get(reg);
        if (byFrom == null) {
            throw new InstituteAdminException(400, "IADM07_INVALID_TRANSITION",
                    "Unknown register type: " + register);
        }
        Set<String> allowed = byFrom.get(f);
        if (allowed == null || !allowed.contains(t)) {
            throw new InstituteAdminException(400, "IADM07_INVALID_TRANSITION",
                    "Transition " + f + " → " + t + " is not allowed for " + reg);
        }
    }

    public static boolean allows(String register, String from, String to) {
        try {
            assertTransition(register, from, to);
            return true;
        } catch (InstituteAdminException ex) {
            return false;
        }
    }
}