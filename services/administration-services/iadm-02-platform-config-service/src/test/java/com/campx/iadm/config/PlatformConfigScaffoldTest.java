package com.campx.iadm.config;

import com.campx.iadm.config.server.Iadm02Server;
import com.campx.iadm.config.service.ConfigDomainService;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Scaffold contract tests for IADM-02 Platform Configuration service shell.
 */
public class PlatformConfigScaffoldTest {

    @Test
    public void defaultPortIs8100() {
        assertEquals(8100, Iadm02Server.DEFAULT_PORT);
    }

    @Test
    public void healthPathConstantsExist() {
        assertEquals("/api/v1/iadm/02", Iadm02Server.CONTEXT_PREFIX);
        assertEquals("/api/v1/iadm/02/health", Iadm02Server.HEALTH_PATH);
        assertEquals("/health", Iadm02Server.ROOT_HEALTH_PATH);
        assertTrue(Iadm02Server.HEALTH_PATH.startsWith(Iadm02Server.CONTEXT_PREFIX));
    }

    @Test
    public void configDomainServiceHealthReturnsUp() {
        ConfigDomainService service = new ConfigDomainService();
        Map<String, Object> health = service.health();
        assertNotNull(health);
        assertEquals("UP", health.get("status"));
        assertEquals("IADM-02", health.get("service"));
    }
}
