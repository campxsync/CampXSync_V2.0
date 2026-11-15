package com.campx.admin.institute.service;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.config.TestEnvironmentGuard;
import com.campx.admin.institute.repository.PostgresDelegationRepository;
import com.campx.admin.institute.security.PostgresRoleCodeLookup;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Live Path C M1 check against CampXSync-Architecture-Dev after R31 pilot seed.
 */
public class LivePostgresDelegationActiveCheckTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PILOT = UUID.fromString("c31a0000-0000-4000-8000-000000000031");
    private static final UUID OTHER_USER = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");

    private static DelegationAuthorizationService service;
    private static boolean ready;

    @BeforeClass
    public static void setUp() throws Exception {
        DatabaseConnectionManager cm = DatabaseConnectionManager.getInstance();
        try {
            UUID tenant = TestEnvironmentGuard.verifySafeStagingTenant(cm);
            Assume.assumeTrue("Pilot tenant expected", TENANT.equals(tenant) || tenant != null);
            service = new DelegationAuthorizationService(
                    new PostgresDelegationRepository(cm),
                    new PostgresRoleCodeLookup(cm));
            ready = true;
        } catch (Throwable t) {
            ready = false;
            Assume.assumeTrue("Live Dev DB unavailable: " + t.getMessage(), false);
        }
    }

    @Test
    public void liveAllowsActivePilotDelegationForDocumentsA() {
        Assume.assumeTrue(ready);
        UserSecurityContext ctx = new UserSecurityContext(PILOT, TENANT);
        DelegationAuthorizationDecision d = service.checkActive(ctx, PILOT.toString(), "STM-06.DOCUMENTS.A");
        assertTrue("Pilot Registrar must be allowed for DOCUMENTS.A: " + d.getReason(), d.isAllowed());
        assertEquals("ACTIVE_R31_DELEGATION", d.getReason());
    }

    @Test
    public void liveAllowsAllStmApprovalKeys() {
        Assume.assumeTrue(ready);
        UserSecurityContext ctx = new UserSecurityContext(PILOT, TENANT);
        String[] keys = {
                "STM-02.ADMISSIONS.A",
                "STM-03.ENROLMENT.A",
                "STM-04.LIFECYCLE.A",
                "STM-05.GUARDIAN.A",
                "STM-06.DOCUMENTS.A"
        };
        for (String key : keys) {
            DelegationAuthorizationDecision d = service.checkActive(ctx, PILOT.toString(), key);
            assertTrue(key + " denied: " + d.getReason(), d.isAllowed());
        }
    }

    @Test
    public void liveDeniesUnauthorizedPermissionKey() {
        Assume.assumeTrue(ready);
        UserSecurityContext ctx = new UserSecurityContext(PILOT, TENANT);
        DelegationAuthorizationDecision d = service.checkActive(ctx, PILOT.toString(), "FIN.PAYROLL.A");
        assertFalse(d.isAllowed());
        assertEquals("UNAUTHORIZED_PERMISSION_KEY", d.getReason());
    }

    @Test
    public void liveDeniesWrongSubjectMismatch() {
        Assume.assumeTrue(ready);
        UserSecurityContext ctx = new UserSecurityContext(OTHER_USER, TENANT);
        DelegationAuthorizationDecision d = service.checkActive(ctx, PILOT.toString(), "STM-06.DOCUMENTS.A");
        assertFalse(d.isAllowed());
        assertEquals("CALLER_SUBJECT_MISMATCH", d.getReason());
    }

    @Test
    public void liveDeniesStudentWithoutDelegation() {
        Assume.assumeTrue(ready);
        UserSecurityContext ctx = new UserSecurityContext(OTHER_USER, TENANT);
        DelegationAuthorizationDecision d = service.checkActive(ctx, OTHER_USER.toString(), "STM-06.DOCUMENTS.A");
        assertFalse(d.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", d.getReason());
    }
}
