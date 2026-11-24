package com.campx.iadm.access;

import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.service.GovernanceReadService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * IADM-03 US-030/031 — offline denials and governance contract (no DB).
 */
public class CatalogChangeMatrixTest {

    @Test
    public void onlyR01HoldsCatalogChangeKeys() {
        Assert.assertTrue(AccessPermissionMatrix.allows("R01", "iam.catalog.change.propose"));
        Assert.assertTrue(AccessPermissionMatrix.allows("R01", "iam.catalog.change.approve"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R02", "iam.catalog.change.propose"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R02", "iam.catalog.change.approve"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R03", "iam.catalog.change.propose"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R03", "iam.catalog.change.approve"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R04", "iam.catalog.change.propose"));
    }

    @Test
    public void r02AndR03HoldGovernanceReadAppropriately() {
        Assert.assertTrue(AccessPermissionMatrix.allows("R01", "iam.governance.read"));
        Assert.assertTrue(AccessPermissionMatrix.allows("R02", "iam.governance.read"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R03", "iam.governance.read"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R04", "iam.governance.read"));
    }

    @Test
    public void claimsContractIsNotPerRequest() {
        Map<String, Object> c = new GovernanceReadService().claimsContract();
        Assert.assertEquals(Boolean.FALSE, c.get("iadm03OnRequestPath"));
        Assert.assertEquals(Boolean.FALSE, c.get("roleNameFallback"));
        Assert.assertEquals(Integer.valueOf(300), c.get("gatewayIdentityCacheTtlSeconds"));
    }

    @Test
    public void ceilingStillDeniesR03SelfAndR01() {
        Assert.assertNotNull(GrantorCeiling.denyReason("R03", "INSTITUTION_ADMIN", false));
        Assert.assertNotNull(GrantorCeiling.denyReason("R01", "FACULTY", true));
    }
}
