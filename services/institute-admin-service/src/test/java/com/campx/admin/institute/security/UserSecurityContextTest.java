package com.campx.admin.institute.security;

import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link UserSecurityContext}.
 * Validates header extraction, UUID validation, and error mappings.
 */
public class UserSecurityContextTest {

    @Test
    public void testValidHeaderExtraction() {
        String userIdStr = UUID.randomUUID().toString();
        String tenantIdStr = UUID.randomUUID().toString();

        UserSecurityContext ctx = UserSecurityContext.fromHeaders(userIdStr, tenantIdStr);

        assertNotNull(ctx);
        assertEquals(userIdStr, ctx.getUserId().toString());
        assertEquals(tenantIdStr, ctx.getTenantId().toString());
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testMissingUserIdHeader() {
        String tenantIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders(null, tenantIdStr);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testBlankUserIdHeader() {
        String tenantIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders("   ", tenantIdStr);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testMissingTenantIdHeader() {
        String userIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders(userIdStr, null);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testBlankTenantIdHeader() {
        String userIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders(userIdStr, "");
    }

    @Test(expected = SecurityViolationException.class)
    public void testInvalidUserIdFormat() {
        String tenantIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders("not-a-uuid", tenantIdStr);
    }

    @Test(expected = SecurityViolationException.class)
    public void testInvalidTenantIdFormat() {
        String userIdStr = UUID.randomUUID().toString();
        UserSecurityContext.fromHeaders(userIdStr, "invalid-tenant-uuid");
    }

    @Test(expected = SecurityViolationException.class)
    public void testNullUserIdConstructor() {
        new UserSecurityContext(null, UUID.randomUUID());
    }

    @Test(expected = SecurityViolationException.class)
    public void testNullTenantIdConstructor() {
        new UserSecurityContext(UUID.randomUUID(), null);
    }
}
