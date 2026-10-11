package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryDelegationRepositoryTest {

    private InMemoryDelegationRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID fromUser;
    private UUID toUser;
    private UUID roleId;

    @Before
    public void setUp() {
        repository = new InMemoryDelegationRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());

        fromUser = UUID.randomUUID();
        toUser = UUID.randomUUID();
        roleId = UUID.randomUUID();
    }

    @Test
    public void testCreateAndRetrieveDelegation() {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUser.toString());
        delegation.setToUserId(toUser.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setReason("Annual leave coverage");
        delegation.setValidFrom(System.currentTimeMillis());
        delegation.setValidTo(System.currentTimeMillis() + 86400000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        assertNotNull(created.getId());
        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals("Annual leave coverage", created.getReason());
        assertEquals("ACTIVE", created.getStatus());

        Optional<Delegation> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        List<Delegation> list = repository.listDelegations(tenantContext);
        assertEquals(1, list.size());

        List<Delegation> forUser = repository.listDelegationsForUser(tenantContext, fromUser.toString());
        assertEquals(1, forUser.size());

        List<Delegation> forTarget = repository.listDelegationsForUser(tenantContext, toUser.toString());
        assertEquals(1, forTarget.size());
    }

    @Test
    public void testUpdateDelegation() {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUser.toString());
        delegation.setToUserId(toUser.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setValidFrom(System.currentTimeMillis());
        delegation.setValidTo(System.currentTimeMillis() + 3600000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);

        Delegation update = new Delegation();
        update.setId(created.getId());
        update.setReason("Extended medical leave coverage");
        update.setValidTo(System.currentTimeMillis() + 7200000L);

        Delegation updated = repository.updateDelegation(tenantContext, update);
        assertEquals("Extended medical leave coverage", updated.getReason());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testRevokeDelegation() {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUser.toString());
        delegation.setToUserId(toUser.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setValidFrom(System.currentTimeMillis());
        delegation.setValidTo(System.currentTimeMillis() + 3600000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);
        repository.revokeDelegation(tenantContext, created.getId());

        Optional<Delegation> afterRevoke = repository.findById(tenantContext, created.getId());
        assertFalse(afterRevoke.isPresent());
    }

    @Test
    public void testTenantIsolation() {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUser.toString());
        delegation.setToUserId(toUser.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setValidFrom(System.currentTimeMillis());
        delegation.setValidTo(System.currentTimeMillis() + 3600000L);

        Delegation created = repository.createDelegation(tenantContext, delegation);

        Optional<Delegation> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse(cross.isPresent());

        List<Delegation> otherList = repository.listDelegations(otherTenantContext);
        assertTrue(otherList.isEmpty());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testInvalidDatesThrow() {
        Delegation delegation = new Delegation();
        delegation.setFromUserId(fromUser.toString());
        delegation.setToUserId(toUser.toString());
        delegation.setRoleId(roleId.toString());
        delegation.setValidFrom(System.currentTimeMillis() + 10000L);
        delegation.setValidTo(System.currentTimeMillis());

        repository.createDelegation(tenantContext, delegation);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listDelegations(null);
    }
}
