package com.campx.iadm.access;

import com.campx.iadm.access.server.Iadm03Server;
import com.campx.iadm.access.service.AccessGovernanceService;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IdentityAccessScaffoldTest {

    @Test
    public void defaultPortIs8101() {
        assertEquals(8101, Iadm03Server.DEFAULT_PORT);
    }

    @Test
    public void healthPathConstantsExist() {
        assertEquals("/api/v1/iadm/03", Iadm03Server.CONTEXT_PREFIX);
        assertEquals("/api/v1/iadm/03/health", Iadm03Server.HEALTH_PATH);
        assertEquals("/health", Iadm03Server.ROOT_HEALTH_PATH);
        assertTrue(Iadm03Server.HEALTH_PATH.startsWith(Iadm03Server.CONTEXT_PREFIX));
    }

    @Test
    public void healthReturnsUp() {
        AccessGovernanceService service = new AccessGovernanceService();
        Map<String, Object> health = service.health();
        assertNotNull(health);
        assertEquals("UP", health.get("status"));
        assertEquals("IADM-03", health.get("service"));
        assertEquals("DORMANT", health.get("adm03Family"));
        assertEquals("NOT_PER_REQUEST", health.get("runtimePath"));
    }
}
