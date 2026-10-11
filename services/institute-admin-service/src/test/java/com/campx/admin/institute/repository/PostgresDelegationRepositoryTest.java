package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.Delegation;
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
 * Live integration tests for Item 18: {@link PostgresDelegationRepository} against Supabase PostgreSQL.
 * Verifies delegation persistence, user resolution, update, soft-delete revocation, and tenant isolation.
 */
public class PostgresDelegationRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresDelegationRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;
    private static UUID roleId;
    private static UUID fromUserId;
    private static UUID toUserId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdUserIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Set<UUID> createdDelegationIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresDelegationRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM iam.roles WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        roleId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);
        assumeNotNull("Requires an active role in tenant", roleId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);

        fromUserId = createTestUserWithProfile("from_user");
        toUserId = createTestUserWithProfile("to_user");

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_id, created_at, updated_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, now(), now())")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, fromUserId);
                ps.setObject(3, roleId);
                ps.executeUpdate();
            }
        }
    }

    private static UUID createTestUserWithProfile(String prefix) throws Exception {
        UUID uid = UUID.randomUUID();
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auth.users (id, email) VALUES (?, ?)")) {
                ps.setObject(1, uid);
                ps.setString(2, prefix + "_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')")) {
                ps.setObject(1, uid);
                ps.setObject(2, tenantId);
                ps.setString(3, prefix + "_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.setString(4, "Delegation Test " + prefix);
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
            for (UUID delId : createdDelegationIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.delegations WHERE id = ?")) {
                    ps.setObject(1, delId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID uid : createdUserIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.role_assignments WHERE user_id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.user_profiles WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auth.users WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveDelegation() throws Exception {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUserId.toString());
        delegation.setToUserId(toUserId.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setReason("Dean sabbatical coverage");
        delegation.setValidFrom(System.currentTimeMillis() - 60000L);
        delegation.setValidTo(System.currentTimeMillis() + 86400000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        assertNotNull(created.getId());
        createdDelegationIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(fromUserId.toString(), created.getFromUserId());
        assertEquals(toUserId.toString(), created.getToUserId());
        assertEquals(roleId.toString(), created.getRoleId());
        assertEquals("Dean sabbatical coverage", created.getReason());
        assertEquals("ACTIVE", created.getStatus());

        // Find by ID
        Optional<Delegation> byId = repository.findById(tenantContext, created.getId());
        assertTrue("Delegation must be retrievable by ID", byId.isPresent());
        assertEquals("Dean sabbatical coverage", byId.get().getReason());

        // List delegations
        List<Delegation> list = repository.listDelegations(tenantContext);
        assertFalse(list.isEmpty());

        // List for delegating user
        List<Delegation> forFrom = repository.listDelegationsForUser(tenantContext, fromUserId.toString());
        assertFalse(forFrom.isEmpty());

        // List for target user
        List<Delegation> forTo = repository.listDelegationsForUser(tenantContext, toUserId.toString());
        assertFalse(forTo.isEmpty());
    }

    @Test
    public void testUpdateDelegation() throws Exception {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUserId.toString());
        delegation.setToUserId(toUserId.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setReason("Original coverage reason");
        delegation.setValidFrom(System.currentTimeMillis() - 60000L);
        delegation.setValidTo(System.currentTimeMillis() + 86400000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        createdDelegationIds.add(UUID.fromString(created.getId()));

        Delegation update = new Delegation();
        update.setId(created.getId());
        update.setReason("Updated coverage reason: extended sabbatical");
        update.setValidTo(System.currentTimeMillis() + 172800000L);

        Delegation updated = repository.updateDelegation(tenantContext, update);
        assertEquals("Updated coverage reason: extended sabbatical", updated.getReason());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testRevokeDelegation() throws Exception {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUserId.toString());
        delegation.setToUserId(toUserId.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setReason("Delegation to be revoked");
        delegation.setValidFrom(System.currentTimeMillis() - 60000L);
        delegation.setValidTo(System.currentTimeMillis() + 86400000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        createdDelegationIds.add(UUID.fromString(created.getId()));

        repository.revokeDelegation(tenantContext, created.getId());

        Optional<Delegation> afterRevoke = repository.findById(tenantContext, created.getId());
        assertFalse("Revoked delegation should not be returned by findById", afterRevoke.isPresent());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUserId.toString());
        delegation.setToUserId(toUserId.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setReason("Tenant isolated delegation");
        delegation.setValidFrom(System.currentTimeMillis() - 60000L);
        delegation.setValidTo(System.currentTimeMillis() + 86400000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        createdDelegationIds.add(UUID.fromString(created.getId()));

        Optional<Delegation> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must not find delegation", cross.isPresent());
    }
}
