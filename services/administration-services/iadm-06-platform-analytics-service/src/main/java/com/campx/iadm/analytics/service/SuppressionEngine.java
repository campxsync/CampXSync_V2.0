package com.campx.iadm.analytics.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small-group suppression and subtraction reconstruction guard (IADM-06-US-024 / D4).
 * Plain language: hide platform/cross-institute groups below the runtime threshold;
 * also hide complementary groups so a withheld value cannot be rebuilt from a total.
 * Own-institute views are not suppressed by the caller (this engine only suppresses when asked).
 */
public final class SuppressionEngine {

    /** DEMO ONLY default documented in blueprint — not a privacy standard. Production = OWNER-OPEN. */
    public static final int DOCUMENTED_DEMO_DEFAULT = 5;

    private static volatile int threshold = 1; // DEMO ONLY runtime start: show figures on small seed

    private SuppressionEngine() {
    }

    public static int getThreshold() {
        return threshold;
    }

    public static void setThreshold(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("threshold must be >= 0");
        }
        threshold = value;
    }

    /**
     * Apply suppression to a list of {key, count} groups.
     * When any group is withheld, withhold complementary groups that would allow subtraction reconstruction
     * if a total is also present (caller should pass includeTotal=true when a total is exposed).
     */
    public static Map<String, Object> suppressGroups(List<Map<String, Object>> groups, boolean platformScope) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (!platformScope) {
            out.put("groups", groups);
            out.put("suppressed", Boolean.FALSE);
            out.put("threshold", Integer.valueOf(threshold));
            out.put("reason", "Own-institute view — suppression not applied");
            return out;
        }
        List<Map<String, Object>> kept = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> withheld = new ArrayList<Map<String, Object>>();
        int total = 0;
        for (int i = 0; i < groups.size(); i++) {
            Map<String, Object> g = groups.get(i);
            int c = toInt(g.get("count"));
            total += c;
            if (c < threshold) {
                Map<String, Object> w = new LinkedHashMap<String, Object>(g);
                w.put("withheld", Boolean.TRUE);
                w.put("reason", "Below small-group threshold");
                w.remove("count");
                withheld.add(w);
            } else {
                kept.add(g);
            }
        }
        // Reconstruction guard: if any group withheld and exactly one complementary kept group remains
        // that would equal (total - withheld), withhold that complementary too when size==1.
        if (!withheld.isEmpty() && kept.size() == 1) {
            Map<String, Object> only = new LinkedHashMap<String, Object>(kept.get(0));
            only.put("withheld", Boolean.TRUE);
            only.put("reason", "Withheld to prevent reconstruction by subtraction");
            only.remove("count");
            withheld.add(only);
            kept = new ArrayList<Map<String, Object>>();
        }
        out.put("groups", kept);
        out.put("withheldGroups", withheld);
        out.put("suppressed", Boolean.valueOf(!withheld.isEmpty()));
        out.put("threshold", Integer.valueOf(threshold));
        out.put("thresholdLabel", "DEMO ONLY — not a privacy standard");
        if (!withheld.isEmpty()) {
            out.put("message", "One or more groups withheld under small-group rule (D4)");
        }
        // Never return raw total alongside withheld complementary groups
        if (!withheld.isEmpty()) {
            out.put("totalWithheld", Boolean.TRUE);
        } else {
            out.put("total", Integer.valueOf(total));
        }
        return out;
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
