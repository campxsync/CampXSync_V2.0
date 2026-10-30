package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.ApiClient;
import com.campx.admin.institute.model.InstituteModels.ApiClientGrant;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class InMemoryApiClientRepositoryTest {

    private InMemoryApiClientRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryApiClientRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieve() {
        ApiClient client = new ApiClient();
        client.setName("Canvas Sync");
        client.setUserId(UUID.randomUUID().toString());
        client.setDescription("Canvas LMS Connector");

        ApiClient created = repository.createApiClient(tenantContext, client);
        assertNotNull(created.getId());
        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals("Canvas Sync", created.getName());
        assertEquals("ACTIVE", created.getStatus());

        Optional<ApiClient> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals("Canvas LMS Connector", byId.get().getDescription());

        List<ApiClient> list = repository.listApiClients(tenantContext);
        assertEquals(1, list.size());
    }

    @Test
    public void testUpdateClient() {
        ApiClient client = new ApiClient();
        client.setName("Initial Name");
        client.setUserId(UUID.randomUUID().toString());
        ApiClient created = repository.createApiClient(tenantContext, client);

        ApiClient update = new ApiClient();
        update.setId(created.getId());
        update.setName("Updated Name");
        update.setDescription("New Desc");
        update.setStatus("SUSPENDED");
        update.setAllowedIps(Arrays.asList("10.0.0.1"));

        ApiClient updated = repository.updateApiClient(tenantContext, update);
        assertEquals("Updated Name", updated.getName());
        assertEquals("New Desc", updated.getDescription());
        assertEquals("SUSPENDED", updated.getStatus());
        assertEquals(1, updated.getAllowedIps().size());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testRevokeClient() {
        ApiClient client = new ApiClient();
        client.setName("To Revoke");
        client.setUserId(UUID.randomUUID().toString());
        ApiClient created = repository.createApiClient(tenantContext, client);

        repository.revokeApiClient(tenantContext, created.getId());
        Optional<ApiClient> afterRevoke = repository.findById(tenantContext, created.getId());
        assertFalse(afterRevoke.isPresent());
    }

    @Test
    public void testGrantsLifecycle() {
        ApiClient client = new ApiClient();
        client.setName("Grant Target");
        client.setUserId(UUID.randomUUID().toString());
        ApiClient created = repository.createApiClient(tenantContext, client);

        ApiClientGrant grant = new ApiClientGrant();
        grant.setClientId(created.getId());
        grant.setPermissionId(UUID.randomUUID().toString());
        grant.setResourceScope("{\"dept\":\"CS\"}");

        ApiClientGrant added = repository.addGrant(tenantContext, grant);
        assertNotNull(added.getId());
        assertEquals(created.getId(), added.getClientId());

        List<ApiClientGrant> list = repository.listGrants(tenantContext, created.getId());
        assertEquals(1, list.size());

        repository.revokeGrant(tenantContext, added.getId());
        List<ApiClientGrant> listAfterRevoke = repository.listGrants(tenantContext, created.getId());
        assertTrue(listAfterRevoke.isEmpty());
    }

    @Test
    public void testTenantIsolation() {
        ApiClient client = new ApiClient();
        client.setName("Tenant Isolated");
        client.setUserId(UUID.randomUUID().toString());
        ApiClient created = repository.createApiClient(tenantContext, client);

        Optional<ApiClient> cross = repository.findById(otherContext, created.getId());
        assertFalse(cross.isPresent());

        List<ApiClient> listOther = repository.listApiClients(otherContext);
        assertTrue(listOther.isEmpty());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listApiClients(null);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testMissingNameThrows() {
        ApiClient client = new ApiClient();
        client.setUserId(UUID.randomUUID().toString());
        repository.createApiClient(tenantContext, client);
    }
}
