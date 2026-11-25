package com.campx.iadm.analytics;

import com.campx.iadm.analytics.security.AnalyticsPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-06-US-018 — offline denial proofs for permission-key mapping.
 */
public class AnalyticsPermissionMatrixTest {

    @Test
    public void r01HasViewAndExportNotManage() {
        assertTrue(AnalyticsPermissionMatrix.allows("R01", "iadm06.platform.view"));
        assertTrue(AnalyticsPermissionMatrix.allows("R01", "iadm06.export.request"));
        assertFalse(AnalyticsPermissionMatrix.allows("R01", "iadm06.catalogue.manage"));
    }

    @Test
    public void r02ViewAndExportNoManage() {
        assertTrue(AnalyticsPermissionMatrix.allows("R02", "iadm06.platform.view"));
        assertTrue(AnalyticsPermissionMatrix.allows("R02", "iadm06.export.request"));
        assertFalse(AnalyticsPermissionMatrix.allows("R02", "iadm06.catalogue.manage"));
    }

    @Test
    public void r03OwnInstituteOnly() {
        assertTrue(AnalyticsPermissionMatrix.allows("R03", "iadm06.institute.view"));
        assertTrue(AnalyticsPermissionMatrix.allows("R03", "iadm06.export.request"));
        assertFalse(AnalyticsPermissionMatrix.allows("R03", "iadm06.platform.view"));
    }

    @Test
    public void r04Denied() {
        assertFalse(AnalyticsPermissionMatrix.allows("R04", "iadm06.platform.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R04", "iadm06.institute.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R04", "iadm06.export.request"));
    }

    @Test
    public void r28ViewOnlyNeverExport() {
        assertTrue(AnalyticsPermissionMatrix.allows("R28", "iadm06.platform.view"));
        assertTrue(AnalyticsPermissionMatrix.allows("R28", "iadm06.catalogue.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R28", "iadm06.export.request"));
        assertFalse(AnalyticsPermissionMatrix.allows("R28", "iadm06.export.view"));
    }

    @Test
    public void otherRolesDenied() {
        assertFalse(AnalyticsPermissionMatrix.allows("R05", "iadm06.platform.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R10", "iadm06.institute.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R25", "iadm06.catalogue.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R27", "iadm06.platform.view"));
        assertFalse(AnalyticsPermissionMatrix.allows("R31", "iadm06.institute.view"));
    }
}
