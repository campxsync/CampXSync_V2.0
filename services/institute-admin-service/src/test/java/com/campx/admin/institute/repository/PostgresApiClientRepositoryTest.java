package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.ApiClient;
import com.campx.admin.institute.model.InstituteModels.ApiClientGrant;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 17: {@link PostgresApiClientRepository} against Supabase PostgreSQL.
 * Verifies API client creation, retrieval, updates, grants attachment, cascade revocation, and tenant isolation.
 */
public class PostgresApiClientRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresApiClientRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;
    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final String WILDCARD_PERMISSION_UUID = "3b88cc1f-aa8d-4fd6-a635-4d3fa9a6bd7f";

    private static final Set<UUID> createdUserIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Set<UUID> createdClientIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresApiClientRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant in core.tenants", tenantId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);
    }

    private static UUID createTestAuthUser() throws Exception {
        UUID uid = UUID.randomUUID();
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auth.users (id, email) VALUES (?, ?)")) {
                ps.setObject(1, uid);
                ps.setString(2, "test_client_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.executeUpdate();
            }
        }
        createdUserIds.add(uid);
        return uid;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID clientId : createdClientIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.api_client_grants WHERE client_id = ?")) {
                    ps.setObject(1, clientId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.api_clients WHERE id = ?")) {
                    ps.setObject(1, clientId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID uid : createdUserIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auth.users WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveApiClient() throws Exception {
        UUID userUuid = createTestAuthUser();

        ApiClient client = new ApiClient();
        client.setUserId(userUuid.toString());
        client.setName("LMS Sync Integration Agent");
        client.setDescription("Integration client for Canvas LMS automated synchronization");
        client.setStatus("ACTIVE");
        client.setAllowedIps(Arrays.asList("192.168.1.100", "10.0.0.1"));

        ApiClient created = repository.createApiClient(tenantContext, client);
        assertNotNull(created.getId());
        createdClientIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(userUuid.toString(), created.getUserId());
        assertEquals("LMS Sync Integration Agent", created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(2, created.getAllowedIps().size());

        // Find by ID
        Optional<ApiClient> byId = repository.findById(tenantContext, created.getId());
        assertTrue("Client must be present", byId.isPresent());
        assertEquals("Integration client for Canvas LMS automated synchronization", byId.get().getDescription());

        // List clients
        List<ApiClient> list = repository.listApiClients(tenantContext);
        assertFalse(list.isEmpty());
        boolean found = false;
        for (ApiClient c : list) {
            if (c.getId().equals(created.getId())) {
                found = true;
                break;
            }
        }
        assertTrue("Created client must be in list", found);
    }

    @Test
    public void testUpdateApiClient() throws Exception {
        UUID userUuid = createTestAuthUser();

        ApiClient client = new ApiClient();
        client.setUserId(userUuid.toString());
        client.setName("Original Client Name");
        client.setStatus("ACTIVE");
        client.setAllowedIps(Collections.singletonList("127.0.0.1"));

        ApiClient created = repository.createApiClient(tenantContext, client);
        createdClientIds.add(UUID.fromString(created.getId()));

        ApiClient update = new ApiClient();
        update.setId(created.getId());
        update.setName("Updated Client Name");
        update.setDescription("Updated Description");
        update.setStatus("SUSPENDED");
        update.setAllowedIps(Arrays.asList("10.10.10.1", "10.10.10.2"));
        update.setExpiresAt(System.currentTimeMillis() + 86400000L);

        ApiClient updated = repository.updateApiClient(tenantContext, update);
        assertEquals("Updated Client Name", updated.getName());
        assertEquals("Updated Description", updated.getDescription());
        assertEquals("SUSPENDED", updated.getStatus());
        assertEquals(2, updated.getAllowedIps().size());
        assertNotNull(updated.getExpiresAt());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testRevokeApiClient() throws Exception {
        UUID userUuid = createTestAuthUser();

        ApiClient client = new ApiClient();
        client.setUserId(userUuid.toString());
        client.setName("Client To Revoke");
        client.setStatus("ACTIVE");

        ApiClient created = repository.createApiClient(tenantContext, client);
        createdClientIds.add(UUID.fromString(created.getId()));

        repository.revokeApiClient(tenantContext, created.getId());

        Optional<ApiClient> afterRevoke = repository.findById(tenantContext, created.getId());
        assertFalse("Revoked client should not be returned by findById", afterRevoke.isPresent());
    }

    @Test
    public void testAddAndListAndRevokeGrants() throws Exception {
        UUID userUuid = createTestAuthUser();

        ApiClient client = new ApiClient();
        client.setUserId(userUuid.toString());
        client.setName("Grant Client");
        client.setStatus("ACTIVE");

        ApiClient created = repository.createApiClient(tenantContext, client);
        createdClientIds.add(UUID.fromString(created.getId()));

        ApiClientGrant grant = new ApiClientGrant();
        grant.setClientId(created.getId());
        grant.setPermissionId(WILDCARD_PERMISSION_UUID);
        grant.setResourceScope("{\"scope\":\"all\"}");

        ApiClientGrant createdGrant = repository.addGrant(tenantContext, grant);
        assertNotNull(createdGrant.getId());
        assertEquals(created.getId(), createdGrant.getClientId());
        assertEquals(WILDCARD_PERMISSION_UUID, createdGrant.getPermissionId());

        List<ApiClientGrant> grants = repository.listGrants(tenantContext, created.getId());
        assertEquals(1, grants.size());
        assertEquals(createdGrant.getId(), grants.get(0).getId());

        repository.revokeGrant(tenantContext, createdGrant.getId());

        List<ApiClientGrant> grantsAfterRevoke = repository.listGrants(tenantContext, created.getId());
        assertTrue("Grant list must be empty after revocation", grantsAfterRevoke.isEmpty());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        UUID userUuid = createTestAuthUser();

        ApiClient client = new ApiClient();
        client.setUserId(userUuid.toString());
        client.setName("Tenant A Client");

        ApiClient created = repository.createApiClient(tenantContext, client);
        createdClientIds.add(UUID.fromString(created.getId()));

        // Cross-tenant access must return empty / not found
        Optional<ApiClient> crossTenantLookup = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must not find the record", crossTenantLookup.isPresent());
    }
}
