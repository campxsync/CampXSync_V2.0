package com.campx.iadm.access;

import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.service.AccessReviewGovernanceService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * IADM-03 S4 — US-034 notification contract + US-035 R27 denials (offline).
 */
public class S4DenialAndNotificationTest {

    @Test
    public void r27DeniedForR01R02R03() {
        Assert.assertNotNull(GrantorCeiling.denyReason("R01", "API_CLIENT", false));
        Assert.assertNotNull(GrantorCeiling.denyReason("R02", "API_CLIENT", false));
        Assert.assertNotNull(GrantorCeiling.denyReason("R03", "API_CLIENT", false));
        Assert.assertTrue(GrantorCeiling.denyReason("R01", "R27", false) != null
                || GrantorCeiling.denyReason("R01", "API_CLIENT", false) != null);
    }

    @Test
    public void reviewManageNotOnR04OrR27() {
        Assert.assertTrue(AccessPermissionMatrix.allows("R01", "iam.review.manage"));
        Assert.assertTrue(AccessPermissionMatrix.allows("R03", "iam.review.manage"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R04", "iam.review.manage"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R27", "iam.review.manage"));
        Assert.assertFalse(AccessPermissionMatrix.allows("R28", "iam.review.manage"));
    }

    @Test
    public void notificationContractSeparatesAuditFromNotify() {
        Map<String, Object> c = new AccessReviewGovernanceService().notificationContract();
        Assert.assertTrue(String.valueOf(c.get("audit")).contains("REQUIRED"));
        Assert.assertTrue(String.valueOf(c.get("notification")).contains("BEST_EFFORT"));
        Assert.assertTrue(String.valueOf(c.get("stoppedAuditService")).toLowerCase().contains("must not report success"));
    }
}
