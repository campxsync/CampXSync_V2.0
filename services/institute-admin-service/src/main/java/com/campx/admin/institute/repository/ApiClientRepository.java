package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.ApiClient;
import com.campx.admin.institute.model.InstituteModels.ApiClientGrant;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing API Clients and Scoped Grants (Item 17: {@code iam.api_clients} & {@code iam.api_client_grants}).
 * Governed by tenant boundary enforcement and RLS policies under {@code iam.has_permission('adm03.*')}.
 */
public interface ApiClientRepository {

    /**
     * Registers a new API Client identity under caller's tenant.
     *
     * @param context caller security context
     * @param client  client entity to persist
     * @return persisted client with assigned UUID and creation timestamps
     */
    ApiClient createApiClient(UserSecurityContext context, ApiClient client);

    /**
     * Retrieves an API Client by primary ID within the caller's tenant.
     *
     * @param context caller security context
     * @param id      API client UUID
     * @return optional containing API client if active and authorized
     */
    Optional<ApiClient> findById(UserSecurityContext context, String id);

    /**
     * Lists active API Clients within the caller's tenant.
     *
     * @param context caller security context
     * @return list of API Clients
     */
    List<ApiClient> listApiClients(UserSecurityContext context);

    /**
     * Updates editable fields of an API Client (name, description, status, allowedIps, expiresAt).
     *
     * @param context caller security context
     * @param client  client entity containing modifications
     * @return updated client record
     */
    ApiClient updateApiClient(UserSecurityContext context, ApiClient client);

    /**
     * Revokes or soft-deletes an API Client.
     *
     * @param context caller security context
     * @param id      API client UUID
     */
    void revokeApiClient(UserSecurityContext context, String id);

    /**
     * Adds a scoped permission grant to an active API Client.
     *
     * @param context caller security context
     * @param grant   grant specification
     * @return persisted grant record
     */
    ApiClientGrant addGrant(UserSecurityContext context, ApiClientGrant grant);

    /**
     * Lists all scoped permission grants for a given API Client.
     *
     * @param context  caller security context
     * @param clientId client UUID
     * @return list of client grants
     */
    List<ApiClientGrant> listGrants(UserSecurityContext context, String clientId);

    /**
     * Revokes a specific permission grant.
     *
     * @param context caller security context
     * @param grantId grant UUID
     */
    void revokeGrant(UserSecurityContext context, String grantId);
}
