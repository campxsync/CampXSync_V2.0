package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.PlatformHealth;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing synthetic service health and heartbeats in {@code plat.service_health}.
 */
public interface ServiceHealthRepository {

    /**
     * Records or updates a heartbeat for a platform service component.
     *
     * @param context the platform admin security context
     * @param health  the health metrics payload
     * @return the recorded platform health record
     */
    PlatformHealth recordHeartbeat(UserSecurityContext context, PlatformHealth health);

    /**
     * Finds the current health status of a named service.
     *
     * @param context     the security context
     * @param serviceName the unique service name
     * @return the health record if present
     */
    Optional<PlatformHealth> findByServiceName(UserSecurityContext context, String serviceName);

    /**
     * Lists current health heartbeats for all active services.
     *
     * @param context the security context
     * @return list of service health snapshots
     */
    List<PlatformHealth> listAll(UserSecurityContext context);
}
