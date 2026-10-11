package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.ApiClient;
import com.campx.admin.institute.model.InstituteModels.ApiClientGrant;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link ApiClientRepository}.
 */
public class InMemoryApiClientRepository implements ApiClientRepository {

    private final Map<String, ApiClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ApiClientGrant> grants = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public ApiClient createApiClient(UserSecurityContext context, ApiClient client) {
        checkContext(context);
        if (client == null || client.getName() == null || client.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("API Client name is required");
        }
        if (client.getUserId() == null || client.getUserId().trim().isEmpty()) {
            throw new MalformedPayloadException("API Client associated user ID is required");
        }

        String id = client.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        ApiClient copy = new ApiClient();
        copy.setId(id);
        copy.setTenantId(context.getTenantId().toString());
        copy.setUserId(client.getUserId().trim());
        copy.setName(client.getName().trim());
        copy.setOwnerUserId(client.getOwnerUserId());
        copy.setDescription(client.getDescription());
        copy.setStatus(client.getStatus() != null ? client.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
        copy.setAllowedIps(client.getAllowedIps());
        copy.setExpiresAt(client.getExpiresAt());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        clients.put(id, copy);
        return copy;
    }

    @Override
    public Optional<ApiClient> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        ApiClient c = clients.get(id);
        if (c != null && c.getDeletedAt() == null && context.getTenantId().toString().equals(c.getTenantId())) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public List<ApiClient> listApiClients(UserSecurityContext context) {
        checkContext(context);
        List<ApiClient> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        for (ApiClient c : clients.values()) {
            if (c.getDeletedAt() == null && tenantStr.equals(c.getTenantId())) {
                result.add(c);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public ApiClient updateApiClient(UserSecurityContext context, ApiClient client) {
        checkContext(context);
        if (client == null || client.getId() == null || client.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for update");
        }
        ApiClient existing = clients.get(client.getId());
        if (existing == null || existing.getDeletedAt() != null || !context.getTenantId().toString().equals(existing.getTenantId())) {
            throw new ResourceNotFoundException("ApiClient", client.getId());
        }

        if (client.getName() != null && !client.getName().trim().isEmpty()) {
            existing.setName(client.getName().trim());
        }
        if (client.getDescription() != null) {
            existing.setDescription(client.getDescription());
        }
        if (client.getStatus() != null && !client.getStatus().trim().isEmpty()) {
            existing.setStatus(client.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (client.getAllowedIps() != null) {
            existing.setAllowedIps(client.getAllowedIps());
        }
        if (client.getExpiresAt() != null) {
            existing.setExpiresAt(client.getExpiresAt());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void revokeApiClient(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for revocation");
        }
        ApiClient existing = clients.get(id);
        if (existing != null && context.getTenantId().toString().equals(existing.getTenantId()) && existing.getDeletedAt() == null) {
            existing.setStatus("REVOKED");
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }

    @Override
    public ApiClientGrant addGrant(UserSecurityContext context, ApiClientGrant grant) {
        checkContext(context);
        if (grant == null || grant.getClientId() == null || grant.getClientId().trim().isEmpty()) {
            throw new MalformedPayloadException("Client ID is required for grant creation");
        }
        if (grant.getPermissionId() == null || grant.getPermissionId().trim().isEmpty()) {
            throw new MalformedPayloadException("Permission ID is required for grant creation");
        }

        String id = grant.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        ApiClientGrant copy = new ApiClientGrant();
        copy.setId(id);
        copy.setTenantId(context.getTenantId().toString());
        copy.setClientId(grant.getClientId().trim());
        copy.setPermissionId(grant.getPermissionId().trim());
        copy.setCollegeId(grant.getCollegeId());
        copy.setDepartmentId(grant.getDepartmentId());
        copy.setResourceScope(grant.getResourceScope() != null ? grant.getResourceScope() : "{}");
        copy.setValidTo(grant.getValidTo());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        grants.put(id, copy);
        return copy;
    }

    @Override
    public List<ApiClientGrant> listGrants(UserSecurityContext context, String clientId) {
        checkContext(context);
        if (clientId == null || clientId.trim().isEmpty()) return Collections.emptyList();
        List<ApiClientGrant> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        for (ApiClientGrant g : grants.values()) {
            if (g.getDeletedAt() == null && tenantStr.equals(g.getTenantId()) && clientId.equals(g.getClientId())) {
                result.add(g);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void revokeGrant(UserSecurityContext context, String grantId) {
        checkContext(context);
        if (grantId == null || grantId.trim().isEmpty()) return;
        ApiClientGrant g = grants.get(grantId);
        if (g != null && context.getTenantId().toString().equals(g.getTenantId()) && g.getDeletedAt() == null) {
            g.setDeletedAt(System.currentTimeMillis());
            g.setUpdatedAt(System.currentTimeMillis());
            g.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }
}
