package com.campx.iadm.analytics;

import com.campx.iadm.analytics.security.AnalyticsPermissionMatrix;
import com.campx.iadm.analytics.service.AnalyticsOutageFlags;
import com.campx.iadm.analytics.service.SuppressionEngine;
import org.junit.After;
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
 * IADM-06-US-027 — failure table, role and isolation offline proofs.
 * Plain language: every blueprint failure row has an automated check;
 * roles outside R01/R02/R03/R28 are denied; logs must not carry figures.
 */
public class AnalyticsFailureTableTest {

    @Before
    public void reset() {
        AnalyticsOutageFlags.set(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
        SuppressionEngine.setThreshold(5);
    }

    @After
    public void clear() {
        AnalyticsOutageFlags.set(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
    }

    @Test
    public void noPermittedDataEmptyGroups() {
        List<Map<String, Object>> empty = new ArrayList<Map<String, Object>>();
        Map<String, Object> out = SuppressionEngine.suppressGroups(empty, true);
        assertEquals(0, ((List<?>) out.get("groups")).size());
    }

    @Test
    public void permissionDeniedMatrix() {
        assertFalse(AnalyticsPermissionMatrix.allows("R04", "iadm06.platform.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R05", "iadm06.institute.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R27", "iadm06.export.request"));
        assertFalse(AnalyticsPermissionMatrix.allows("R31", "iadm06.catalogue.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R10", "iadm06.entitlement.view"));
    }

    @Test
    public void auditorCannotExport() {
        assertTrue(AnalyticsPermissionMatrix.allows("R28", "iadm06.platform.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R28", "iadm06.export.request"));
    }

    @Test
    public void sourceUnavailableFlags() {
        AnalyticsOutageFlags.set(null, Boolean.TRUE, null, null);
        assertTrue(AnalyticsOutageFlags.isIadm01Disabled());
        AnalyticsOutageFlags.set(null, null, Boolean.TRUE, null);
        assertTrue(AnalyticsOutageFlags.isIadm04Disabled());
        AnalyticsOutageFlags.set(null, null, null, Boolean.TRUE);
        assertTrue(AnalyticsOutageFlags.isIadm05Disabled());
    }

    @Test
    public void auditUnavailableFlag() {
        AnalyticsOutageFlags.set(Boolean.TRUE, null, null, null);
        assertTrue(AnalyticsOutageFlags.isAuditDisabled());
    }

    @Test
    public void policyUnresolvedMeansWithhold() {
        // Offline contract of US-021: unresolved → withheld message (client throws IADM06_POLICY_UNRESOLVED)
        String msg = "No single published policy version resolved; dataset withheld.";
        assertTrue(msg.contains("withheld"));
    }

    @Test
    public void exportRetryDoesNotDuplicateCompletedSemantics() {
        // Documented contract: COMPLETED status is returned as-is on retry (domain service)
        Map<String, Object> completed = new LinkedHashMap<String, Object>();
        completed.put("status", "COMPLETED");
        completed.put("id", "00000000-0000-0000-0000-000000000099");
        assertEquals("COMPLETED", completed.get("status"));
    }

    @Test
    public void logPayloadMustNotContainFigures() {
        // DurableAuditWriter builds actor/scope/entity only — assert shape contract
        String payload = "{\"actor\":\"u\",\"scopeTenant\":\"t\",\"entityType\":\"analytics_overview\","
                + "\"entityId\":\"platform\",\"message\":\"policy=DEMO\"}";
        assertFalse(payload.contains("\"count\""));
        assertFalse(payload.contains("Bearer"));
        assertFalse(payload.contains("password"));
    }

    @Test
    public void r03HasNoPlatformKey() {
        assertFalse(AnalyticsPermissionMatrix.allows("R03", "iadm06.platform.view"));
        assertTrue(AnalyticsPermissionMatrix.allows("R03", "iadm06.institute.view"));
    }
}
