package com.campx.iadm.security;

import com.campx.iadm.security.server.Iadm07Server;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * IADM-07-US-022 — scaffold port and route prefix contracts.
 */
public class SecurityScaffoldTest {

    @Test
    public void defaultPortIs8105() {
        assertEquals(8105, Iadm07Server.DEFAULT_PORT);
    }

    @Test
    public void contextPrefixMatchesGateway() {
        assertEquals("/api/v1/iadm/07", Iadm07Server.CONTEXT_PREFIX);
        assertEquals("/api/v1/iadm/07/health", Iadm07Server.HEALTH_PATH);
        assertTrue(Iadm07Server.HEALTH_PATH.startsWith(Iadm07Server.CONTEXT_PREFIX));
    }
}