package com.campx.iadm.config;

import com.campx.iadm.config.service.ConfigDomainService;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Offline validation rules for IADM-02 drafts (US-013 / seed DRAFT-001 vs DRAFT-002).
 */
public class ConfigValidationTest {

    @Test
    public void validSessionTimeoutPasses() {
        List<Map<String, String>> errors = ConfigDomainService.validateValue("security.session_timeout_min", "45");
        assertTrue(errors.isEmpty());
    }

    @Test
    public void invalidAttendanceFailsBounds() {
        List<Map<String, String>> errors = ConfigDomainService.validateValue("attendance.min_pct", "150");
        assertFalse(errors.isEmpty());
        assertEquals("proposedValue", errors.get(0).get("field"));
    }

    @Test
    public void delegationBoundsOneTo365() {
        assertTrue(ConfigDomainService.validateValue("iam.delegation.max_validity_days", "90").isEmpty());
        assertFalse(ConfigDomainService.validateValue("iam.delegation.max_validity_days", "0").isEmpty());
        assertFalse(ConfigDomainService.validateValue("iam.delegation.max_validity_days", "999").isEmpty());
    }

    @Test
    public void defaultPortIs8100() {
        assertEquals(8100, com.campx.iadm.config.server.Iadm02Server.DEFAULT_PORT);
    }
}
