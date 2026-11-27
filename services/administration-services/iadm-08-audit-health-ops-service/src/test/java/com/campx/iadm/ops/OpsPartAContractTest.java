package com.campx.iadm.ops;

import com.campx.iadm.ops.client.Iadm05PolicyClient;
import com.campx.iadm.ops.controller.Iadm08Controller;
import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.service.HealthTargetInventory;
import com.campx.iadm.ops.service.OpsAlertsScope;
import com.campx.iadm.ops.service.OpsDomainService;
import com.campx.iadm.ops.service.OpsOutageFlags;
import com.campx.iadm.ops.service.OpsQueryLimits;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Fix Part A contract tests: alerts scope, acknowledge refusal, health target safety,
 * over-max window, and IADM-05 policy withhold.
 */
public class OpsPartAContractTest {

    private static final UUID OWN = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Before
    public void setUp() {
        OpsQueryLimits.resetDefaults();
        OpsOutageFlags.reset();
    }

    @After
    public void tearDown() {
        OpsQueryLimits.resetDefaults();
        OpsOutageFlags.reset();
    }

    @Test
    public void r03OtherInstituteAlertsRefused() {
        try {
            OpsAlertsScope.resolve(false, OWN, OTHER.toString());
            fail("R03 must not select another institute");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_SCOPE_DENIED", ex.getErrorCode());
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void r03NoParameterGetsOwnInstituteOnly() {
        String scoped = OpsAlertsScope.resolve(false, OWN, null);
        assertEquals(OWN.toString(), scoped);
        assertEquals(OWN.toString(), OpsAlertsScope.resolve(false, OWN, ""));
        assertEquals(OWN.toString(), OpsAlertsScope.resolve(false, OWN, OWN.toString()));
    }

    @Test
    public void platformRolesMaySelectInstitute() {
        assertNull(OpsAlertsScope.resolve(true, OWN, null));
        assertEquals(OTHER.toString(), OpsAlertsScope.resolve(true, OWN, OTHER.toString()));
    }

    @Test
    public void acknowledgeRefusedForEveryRole() {
        String[] roles = new String[]{"R01", "R02", "R03", "R04", "R28"};
        for (String role : roles) {
            Map<String, Object> refused = Iadm08Controller.refuseAcknowledgeForRole(role);
            assertEquals(Integer.valueOf(404), refused.get("httpStatus"));
            assertEquals("IADM08_NOT_AVAILABLE", refused.get("errorCode"));
            assertEquals(Boolean.FALSE, refused.get("acknowledgeSupported"));
            assertTrue(Iadm08Controller.isDeferredMutationPath("/api/v1/iadm/08/alerts/acknowledge"));
            assertTrue(Iadm08Controller.refuseAcknowledgeOrExportJson().contains("IADM08_NOT_AVAILABLE"));
        }
    }

    @Test
    public void healthClientUrlHostAddressRejectedWithoutChangingInventory() {
        int before = HealthTargetInventory.all().size();
        try {
            HealthTargetInventory.rejectClientSuppliedTarget("http://evil", null, null);
            fail("url must be rejected");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_TARGET_REJECTED", ex.getErrorCode());
        }
        try {
            HealthTargetInventory.rejectClientSuppliedTarget(null, "evil.example", null);
            fail("host must be rejected");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_TARGET_REJECTED", ex.getErrorCode());
        }
        try {
            HealthTargetInventory.rejectClientSuppliedTarget(null, null, "10.0.0.1");
            fail("address must be rejected");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_TARGET_REJECTED", ex.getErrorCode());
        }
        assertNull(HealthTargetInventory.find("http://evil"));
        assertEquals(before, HealthTargetInventory.all().size());

        List<Map<String, String>> list = HealthTargetInventory.all();
        Set<String> ids = new HashSet<String>();
        for (Map<String, String> t : list) {
            ids.add(t.get("id"));
        }
        assertTrue(ids.contains("gateway"));
        for (int i = 1; i <= 8; i++) {
            assertTrue("missing iadm-0" + i, ids.contains("iadm-0" + i));
        }
        assertTrue(HealthTargetInventory.SOURCE_LABEL.contains("not live gateway route registry"));
        assertNotNull(HealthTargetInventory.SOURCE_RATIONALE);
        assertTrue(HealthTargetInventory.SOURCE_RATIONALE.toLowerCase().contains("route registry"));
    }

    @Test
    public void timeWindowOverMaxRejectedAsRangeTooLarge() {
        OpsQueryLimits.setMaxWindowDays(31);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(40, ChronoUnit.DAYS);
        try {
            new OpsDomainService().validateMandatoryRange(from.toString(), to.toString());
            fail("over-max window must be rejected");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_RANGE_TOO_LARGE", ex.getErrorCode());
            assertEquals(400, ex.getStatus());
        }
    }

    @Test
    public void missingOrAmbiguousIadm05PolicyWithholdsData() {
        InstituteAdminException missing = Iadm05PolicyClient.mapResolveFailure(
                new InstituteAdminException(404, "NOT_FOUND", "missing"));
        assertEquals("IADM08_POLICY_UNRESOLVED", missing.getErrorCode());
        assertEquals(403, missing.getStatus());

        InstituteAdminException ambiguous = Iadm05PolicyClient.mapResolveFailure(
                new InstituteAdminException(409, "CONFLICT", "ambiguous"));
        assertEquals("IADM08_POLICY_UNRESOLVED", ambiguous.getErrorCode());
        assertEquals(403, ambiguous.getStatus());

        OpsOutageFlags.setIadm05Disabled(true);
        try {
            new Iadm05PolicyClient("http://127.0.0.1:8103/api/v1/iadm/05")
                    .resolveOperationsPolicy(null);
            fail("disabled IADM-05 must withhold");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM08_POLICY_UNAVAILABLE", ex.getErrorCode());
            assertFalse(ex.getMessage().toLowerCase().contains("released"));
        } finally {
            OpsOutageFlags.setIadm05Disabled(false);
        }
    }
}
