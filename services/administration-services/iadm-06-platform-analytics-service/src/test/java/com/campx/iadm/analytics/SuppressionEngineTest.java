package com.campx.iadm.analytics;

import com.campx.iadm.analytics.service.SuppressionEngine;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-06-US-024 — small-group suppression and subtraction reconstruction guard (D4).
 */
public class SuppressionEngineTest {

    @Before
    public void setThreshold() {
        SuppressionEngine.setThreshold(5);
    }

    @Test
    public void ownInstituteNotSuppressed() {
        List<Map<String, Object>> groups = groups(pair("A", 2), pair("B", 3));
        Map<String, Object> out = SuppressionEngine.suppressGroups(groups, false);
        assertFalse(Boolean.TRUE.equals(out.get("suppressed")));
        assertEquals(2, ((List<?>) out.get("groups")).size());
    }

    @Test
    public void platformGroupsBelowThresholdWithheld() {
        // Three groups: one below threshold withheld; two remain (no singleton reconstruction)
        List<Map<String, Object>> groups = groups(pair("A", 2), pair("B", 10), pair("C", 8));
        Map<String, Object> out = SuppressionEngine.suppressGroups(groups, true);
        assertTrue(Boolean.TRUE.equals(out.get("suppressed")));
        assertEquals(2, ((List<?>) out.get("groups")).size());
        assertEquals(1, ((List<?>) out.get("withheldGroups")).size());
    }

    @Test
    public void subtractionGuardWithholdsComplementarySingleton() {
        // A=2 withheld; B=10 would allow reconstructing A from total — withhold B too
        List<Map<String, Object>> groups = groups(pair("A", 2), pair("B", 10));
        Map<String, Object> out = SuppressionEngine.suppressGroups(groups, true);
        assertTrue(Boolean.TRUE.equals(out.get("suppressed")));
        assertEquals(0, ((List<?>) out.get("groups")).size());
        assertEquals(2, ((List<?>) out.get("withheldGroups")).size());
        assertTrue(Boolean.TRUE.equals(out.get("totalWithheld")));
    }

    private static Map<String, Object> pair(String key, int count) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("key", key);
        m.put("count", Integer.valueOf(count));
        return m;
    }

    private static List<Map<String, Object>> groups(Map<String, Object>... items) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < items.length; i++) {
            list.add(items[i]);
        }
        return list;
    }
}
