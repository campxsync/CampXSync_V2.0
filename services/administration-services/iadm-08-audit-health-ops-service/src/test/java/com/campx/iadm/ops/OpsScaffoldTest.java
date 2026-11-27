package com.campx.iadm.ops;

import com.campx.iadm.ops.server.Iadm08Server;
import com.campx.iadm.ops.service.HealthTargetInventory;
import com.campx.iadm.ops.service.OpsDomainService;
import com.campx.iadm.ops.service.OpsQueryLimits;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Scaffold and convention checks (US-016 / US-020 / US-021).
 */
public class OpsScaffoldTest {

    @Test
    public void portAndPrefixMatchPreflight() {
        assertEquals(8106, Iadm08Server.DEFAULT_PORT);
        assertEquals("/api/v1/iadm/08", Iadm08Server.CONTEXT_PREFIX);
    }

    @Test
    public void demoQueryDefaults() {
        OpsQueryLimits.resetDefaults();
        assertEquals(31, OpsQueryLimits.getMaxWindowDays());
        assertEquals(50, OpsQueryLimits.getPageSize());
    }

    @Test
    public void healthInventoryIsServerSideOnly() {
        assertTrue(HealthTargetInventory.all().size() >= 5);
        assertTrue(HealthTargetInventory.SOURCE_LABEL.contains("minimal-read-only"));
        assertTrue(HealthTargetInventory.SOURCE_LABEL.contains("not live gateway route registry"));
        assertEquals(null, HealthTargetInventory.find("http://evil.example"));
        Map<String, String> gw = HealthTargetInventory.find("gateway");
        assertTrue(gw.get("url").startsWith("http://127.0.0.1:"));
    }

    @Test
    public void healthPayloadReportsNoPersistence() {
        Map<String, Object> h = new OpsDomainService().health();
        assertEquals("UP", h.get("status"));
        assertEquals("none", h.get("persistence"));
        assertEquals(Integer.valueOf(8106), h.get("port"));
        assertFalse(Boolean.TRUE.equals(h.get("stored")));
    }
}
