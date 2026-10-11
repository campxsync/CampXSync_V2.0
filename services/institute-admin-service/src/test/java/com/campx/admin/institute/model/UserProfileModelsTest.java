package com.campx.admin.institute.model;

import com.campx.admin.institute.model.UserProfileModels.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link UserProfileModels} verifying allow-list JSON serialization,
 * pagination calculation, and whitelist security rules.
 */
public class UserProfileModelsTest {

    @Test
    public void testUserProfileAllowListJsonSerialization() {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setTenantId(UUID.randomUUID().toString());
        profile.setFullName("John \"Jack\" Doe");
        profile.setEmail("john.doe@example.com");
        profile.setUsername("johndoe");
        profile.setStatus("ACTIVE");
        profile.setPreferences("{\"theme\":\"dark\",\"notifications\":true}");
        profile.setCreatedAt("2026-03-01T10:00:00Z");
        profile.setUpdatedAt("2026-03-02T12:00:00Z");
        profile.setCreatedBy("admin-1");
        profile.setUpdatedBy("admin-2");
        profile.setRowVersion(3);

        String json = profile.toJson();

        // Must contain whitelisted fields
        assertTrue(json.contains("\"id\":\"" + profile.getId() + "\""));
        assertTrue(json.contains("\"tenantId\":\"" + profile.getTenantId() + "\""));
        assertTrue(json.contains("\"fullName\":\"John \\\"Jack\\\" Doe\""));
        assertTrue(json.contains("\"email\":\"john.doe@example.com\""));
        assertTrue(json.contains("\"username\":\"johndoe\""));
        assertTrue(json.contains("\"status\":\"ACTIVE\""));
        assertTrue(json.contains("\"preferences\":{\"theme\":\"dark\",\"notifications\":true}"));
        assertTrue(json.contains("\"rowVersion\":3"));

        // Must NEVER contain password or secret keys
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("token"));
        assertFalse(json.contains("hash"));
    }

    @Test
    public void testUserProfileNullFieldsSerialization() {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setTenantId(UUID.randomUUID().toString());
        profile.setFullName("Jane Doe");
        profile.setEmail("jane@example.com");
        profile.setStatus("ACTIVE");
        profile.setRowVersion(1);

        String json = profile.toJson();

        assertTrue(json.contains("\"personId\":null"));
        assertTrue(json.contains("\"collegeId\":null"));
        assertTrue(json.contains("\"departmentId\":null"));
        assertTrue(json.contains("\"username\":null"));
        assertTrue(json.contains("\"lastSeenAt\":null"));
        assertTrue(json.contains("\"preferences\":{}"));
    }

    @Test
    public void testUserProfilePagePagination() {
        UserProfile u1 = new UserProfile();
        u1.setId(UUID.randomUUID().toString());
        u1.setTenantId(UUID.randomUUID().toString());
        u1.setFullName("User 1");
        u1.setEmail("u1@example.com");
        u1.setStatus("ACTIVE");
        u1.setRowVersion(1);

        UserProfile u2 = new UserProfile();
        u2.setId(UUID.randomUUID().toString());
        u2.setTenantId(UUID.randomUUID().toString());
        u2.setFullName("User 2");
        u2.setEmail("u2@example.com");
        u2.setStatus("ACTIVE");
        u2.setRowVersion(1);

        UserProfilePage page = new UserProfilePage(Arrays.asList(u1, u2), 2, 2, 5);

        assertEquals(2, page.getPage());
        assertEquals(2, page.getLimit());
        assertEquals(5, page.getTotalCount());
        assertEquals(3, page.getTotalPages());

        String json = page.toJson();
        assertTrue(json.contains("\"totalCount\":5"));
        assertTrue(json.contains("\"totalPages\":3"));
        assertTrue(json.contains("\"hasNext\":true"));
        assertTrue(json.contains("\"hasPrevious\":true"));
        assertTrue(json.contains("\"users\":["));
    }

    @Test
    public void testAllowedStatuses() {
        assertEquals(4, UserProfileModels.ALLOWED_STATUSES.size());
        assertTrue(UserProfileModels.ALLOWED_STATUSES.contains("ACTIVE"));
        assertTrue(UserProfileModels.ALLOWED_STATUSES.contains("SUSPENDED"));
        assertTrue(UserProfileModels.ALLOWED_STATUSES.contains("LOCKED"));
        assertTrue(UserProfileModels.ALLOWED_STATUSES.contains("INACTIVE"));
    }

    @Test
    public void testAllowedCreateStatuses() {
        assertEquals(2, UserProfileModels.ALLOWED_CREATE_STATUSES.size());
        assertTrue(UserProfileModels.ALLOWED_CREATE_STATUSES.contains("ACTIVE"));
        assertTrue(UserProfileModels.ALLOWED_CREATE_STATUSES.contains("INACTIVE"));
        assertFalse(UserProfileModels.ALLOWED_CREATE_STATUSES.contains("LOCKED"));
        assertFalse(UserProfileModels.ALLOWED_CREATE_STATUSES.contains("SUSPENDED"));
    }

    @Test
    public void testSortFieldWhitelist() {
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("full_name"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("name"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("email"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("username"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("status"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("created_at"));
        assertTrue(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("updated_at"));

        // SQL injection attempts or invalid columns must not be in whitelist
        assertFalse(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("password"));
        assertFalse(UserProfileModels.SORT_FIELD_WHITELIST.containsKey("1; DROP TABLE users;"));
    }
}
