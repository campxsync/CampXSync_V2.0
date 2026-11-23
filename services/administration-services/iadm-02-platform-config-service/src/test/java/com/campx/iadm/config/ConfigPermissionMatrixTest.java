package com.campx.iadm.config;

import com.campx.iadm.config.security.ConfigPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Allowed and denied unit tests for every new cfg.* key across R01/R02/R03/R04/R28 (D3).
 */
public class ConfigPermissionMatrixTest {

    @Test
    public void everyNewKeyHasAllowAndDenyCoverage() {
        int asserts = 0;
        for (String key : ConfigPermissionMatrix.NEW_KEYS) {
            boolean sawAllow = false;
            boolean sawDeny = false;
            for (String persona : ConfigPermissionMatrix.PERSONAS) {
                boolean allowed = ConfigPermissionMatrix.allows(persona, key);
                if (allowed) {
                    sawAllow = true;
                } else {
                    sawDeny = true;
                }
                asserts++;
            }
            assertTrue("key " + key + " must have at least one allow", sawAllow);
            assertTrue("key " + key + " must have at least one deny", sawDeny);
        }
        assertEquals(ConfigPermissionMatrix.NEW_KEYS.length * ConfigPermissionMatrix.PERSONAS.length, asserts);
    }

    @Test
    public void r01AllowsAllNewKeys() {
        for (String key : ConfigPermissionMatrix.NEW_KEYS) {
            assertTrue(key, ConfigPermissionMatrix.allows("R01", key));
            assertTrue(key, ConfigPermissionMatrix.allows("SUPER_ADMIN", key));
        }
    }

    @Test
    public void r02AllowsCatalogAndDraftOnly() {
        assertTrue(ConfigPermissionMatrix.allows("R02", "cfg.catalog.view"));
        assertTrue(ConfigPermissionMatrix.allows("SYSTEM_ADMIN", "cfg.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R02", "cfg.approve"));
        assertFalse(ConfigPermissionMatrix.allows("R02", "cfg.publish"));
        assertFalse(ConfigPermissionMatrix.allows("R02", "cfg.policy.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R02", "cfg.consumer.read"));
    }

    @Test
    public void r03CatalogViewOnly() {
        assertTrue(ConfigPermissionMatrix.allows("R03", "cfg.catalog.view"));
        assertFalse(ConfigPermissionMatrix.allows("INSTITUTION_ADMIN", "cfg.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R03", "cfg.approve"));
        assertFalse(ConfigPermissionMatrix.allows("R03", "cfg.publish"));
        assertFalse(ConfigPermissionMatrix.allows("R03", "cfg.policy.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R03", "cfg.consumer.read"));
    }

    @Test
    public void r04DeniedAllNewKeys() {
        for (String key : ConfigPermissionMatrix.NEW_KEYS) {
            assertFalse(key, ConfigPermissionMatrix.allows("R04", key));
            assertFalse(key, ConfigPermissionMatrix.allows("COLLEGE_ADMIN", key));
        }
    }

    @Test
    public void r28AuditorCatalogViewOnly() {
        assertTrue(ConfigPermissionMatrix.allows("R28", "cfg.catalog.view"));
        assertTrue(ConfigPermissionMatrix.allows("AUDITOR", "cfg.catalog.view"));
        assertFalse(ConfigPermissionMatrix.allows("R28", "cfg.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R28", "cfg.approve"));
        assertFalse(ConfigPermissionMatrix.allows("R28", "cfg.publish"));
        assertFalse(ConfigPermissionMatrix.allows("R28", "cfg.policy.draft"));
        assertFalse(ConfigPermissionMatrix.allows("R28", "cfg.consumer.read"));
    }

    @Test
    public void fullMatrixTableMatchesOwnerD3() {
        String[][] expected = {
                // key, R01, R02, R03, R04, R28
                {"cfg.catalog.view", "1", "1", "1", "0", "1"},
                {"cfg.draft", "1", "1", "0", "0", "0"},
                {"cfg.approve", "1", "0", "0", "0", "0"},
                {"cfg.publish", "1", "0", "0", "0", "0"},
                {"cfg.policy.draft", "1", "0", "0", "0", "0"},
                {"cfg.consumer.read", "1", "0", "0", "0", "0"},
        };
        for (String[] row : expected) {
            String key = row[0];
            for (int i = 0; i < ConfigPermissionMatrix.PERSONAS.length; i++) {
                boolean want = "1".equals(row[i + 1]);
                boolean got = ConfigPermissionMatrix.allows(ConfigPermissionMatrix.PERSONAS[i], key);
                assertEquals(key + " for " + ConfigPermissionMatrix.PERSONAS[i], want, got);
            }
        }
    }
}
