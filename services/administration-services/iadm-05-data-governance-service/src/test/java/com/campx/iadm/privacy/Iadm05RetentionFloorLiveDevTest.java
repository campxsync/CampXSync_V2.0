package com.campx.iadm.privacy;

import com.campx.iadm.privacy.config.DatabaseConfig;
import com.campx.iadm.privacy.config.DatabaseConnectionManager;
import com.campx.iadm.privacy.exception.InstituteAdminException;
import com.campx.iadm.privacy.security.UserSecurityContext;
import com.campx.iadm.privacy.service.Iadm05DomainService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** Closes IADM-05-US-001 retention ≥ platform minimum. */
public class Iadm05RetentionFloorLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");

    private static boolean dbReady;
    private static Iadm05DomainService service;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
        service = new Iadm05DomainService();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void belowMinimumRejected_atOrAboveAccepted() {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        String code = "WF5R" + (System.currentTimeMillis() % 100000);
        try {
            service.upsertRetention(ctx, code, "GENERIC", 1, "ARCHIVE");
            fail("Expected IADM05_BELOW_MINIMUM");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM05_BELOW_MINIMUM", ex.getErrorCode());
            assertEquals(400, ex.getStatus());
        } catch (RuntimeException ex) {
            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
            assertTrue(c instanceof InstituteAdminException
                    && "IADM05_BELOW_MINIMUM".equals(((InstituteAdminException) c).getErrorCode()));
        }

        Map<String, Object> ok = service.upsertRetention(ctx, code, "GENERIC", 120, "ARCHIVE");
        assertEquals(120, ((Number) ok.get("retentionDays")).intValue());
        assertTrue(((Number) ok.get("platformMinimumDays")).intValue() <= 120);
    }
}
