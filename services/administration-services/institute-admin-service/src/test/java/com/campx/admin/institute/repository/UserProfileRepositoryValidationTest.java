package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.InvalidStatusTransitionException;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.UserProfileModels.*;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.fail;

/**
 * Unit tests validating input sanitization, security constraints,
 * and status transition matrix rules in {@link UserProfileRepository}.
 */
public class UserProfileRepositoryValidationTest {

    private UserProfileRepository repository;
    private UserSecurityContext callerContext;

    @Before
    public void setup() {
        repository = new UserProfileRepository(null);
        callerContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    // =========================================================================
    // Status Transition Matrix Unit Tests
    // =========================================================================

    @Test
    public void testValidStatusTransitions() {
        // ACTIVE transitions
        repository.validateStatusTransition("ACTIVE", "SUSPENDED");
        repository.validateStatusTransition("ACTIVE", "INACTIVE");
        repository.validateStatusTransition("ACTIVE", "ACTIVE");

        // SUSPENDED transitions
        repository.validateStatusTransition("SUSPENDED", "ACTIVE");
        repository.validateStatusTransition("SUSPENDED", "INACTIVE");
        repository.validateStatusTransition("SUSPENDED", "SUSPENDED");

        // INACTIVE transitions
        repository.validateStatusTransition("INACTIVE", "ACTIVE");
        repository.validateStatusTransition("INACTIVE", "INACTIVE");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testTransitionToLockedDisallowed() {
        repository.validateStatusTransition("ACTIVE", "LOCKED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testTransitionFromLockedDisallowed() {
        repository.validateStatusTransition("LOCKED", "ACTIVE");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testTransitionSuspendedToLockedDisallowed() {
        repository.validateStatusTransition("SUSPENDED", "LOCKED");
    }

    // =========================================================================
    // Create Profile Input Validation Unit Tests
    // =========================================================================

    @Test(expected = MalformedPayloadException.class)
    public void testCreateNullRequest() {
        repository.createUser(callerContext, null);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateMissingId() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setFullName("Test User");
        req.setEmail("test@example.com");
        repository.createUser(callerContext, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testCreateInvalidIdFormat() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId("not-a-valid-uuid");
        req.setFullName("Test User");
        req.setEmail("test@example.com");
        repository.createUser(callerContext, req);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateMissingFullName() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setEmail("test@example.com");
        repository.createUser(callerContext, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testCreateFullNameExceedsLength() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) longName.append("a");
        req.setFullName(longName.toString());
        req.setEmail("test@example.com");
        repository.createUser(callerContext, req);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateMissingEmail() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test User");
        repository.createUser(callerContext, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testCreateInvalidEmail() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test User");
        req.setEmail("invalid-email-no-at-sign");
        repository.createUser(callerContext, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testCreateInvalidStatus() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test User");
        req.setEmail("test@example.com");
        req.setStatus("NON_EXISTENT_STATUS");
        repository.createUser(callerContext, req);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateMalformedPreferencesJson() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test User");
        req.setEmail("test@example.com");
        req.setPreferences("{invalid-json:");
        repository.createUser(callerContext, req);
    }

    // =========================================================================
    // Update Profile Input Validation Unit Tests
    // =========================================================================

    @Test(expected = MalformedPayloadException.class)
    public void testUpdateMissingRowVersion() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setFullName("Updated Name");
        repository.updateUser(callerContext, UUID.randomUUID(), req);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testUpdateBlankFullName() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setRowVersion(1);
        req.setFullName("   ");
        repository.updateUser(callerContext, UUID.randomUUID(), req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testUpdateInvalidEmail() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setRowVersion(1);
        req.setEmail("invalid-email");
        repository.updateUser(callerContext, UUID.randomUUID(), req);
    }

    // =========================================================================
    // Update User Status & Self-Deactivation Guard Unit Tests
    // =========================================================================

    @Test(expected = MalformedPayloadException.class)
    public void testUpdateStatusMissingStatus() {
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setRowVersion(1);
        repository.updateUserStatus(callerContext, UUID.randomUUID(), req);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testUpdateStatusMissingRowVersion() {
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setStatus("SUSPENDED");
        repository.updateUserStatus(callerContext, UUID.randomUUID(), req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testUpdateStatusInvalidStatusValue() {
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setStatus("BANNED");
        req.setRowVersion(1);
        repository.updateUserStatus(callerContext, UUID.randomUUID(), req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testSelfSuspensionGuard() {
        // When caller attempts to suspend themselves
        UUID ownUserId = callerContext.getUserId();
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setStatus("SUSPENDED");
        req.setRowVersion(1);
        repository.updateUserStatus(callerContext, ownUserId, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testSelfDeactivationGuard() {
        // When caller attempts to deactivate themselves
        UUID ownUserId = callerContext.getUserId();
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setStatus("INACTIVE");
        req.setRowVersion(1);
        repository.updateUserStatus(callerContext, ownUserId, req);
    }

    // =========================================================================
    // Search Query Wildcard Escaping Unit Tests
    // =========================================================================

    @Test
    public void testEscapeIlikePattern() {
        org.junit.Assert.assertEquals("", UserProfileRepository.escapeIlikePattern(null));
        org.junit.Assert.assertEquals("plain", UserProfileRepository.escapeIlikePattern("plain"));
        org.junit.Assert.assertEquals("100\\%", UserProfileRepository.escapeIlikePattern("100%"));
        org.junit.Assert.assertEquals("john\\_doe", UserProfileRepository.escapeIlikePattern("john_doe"));
        org.junit.Assert.assertEquals("path\\\\to", UserProfileRepository.escapeIlikePattern("path\\to"));
        org.junit.Assert.assertEquals("100\\%\\_safe\\\\path", UserProfileRepository.escapeIlikePattern("100%_safe\\path"));
    }

    // =========================================================================
    // Create Status Restriction Unit Tests
    // =========================================================================

    @Test(expected = SecurityViolationException.class)
    public void testCreateLockedStatusRejected() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test Admin");
        req.setEmail("test@example.com");
        req.setStatus("LOCKED");
        repository.createUser(callerContext, req);
    }

    @Test(expected = SecurityViolationException.class)
    public void testCreateSuspendedStatusRejected() {
        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(UUID.randomUUID().toString());
        req.setFullName("Test Admin");
        req.setEmail("test@example.com");
        req.setStatus("SUSPENDED");
        repository.createUser(callerContext, req);
    }
}
