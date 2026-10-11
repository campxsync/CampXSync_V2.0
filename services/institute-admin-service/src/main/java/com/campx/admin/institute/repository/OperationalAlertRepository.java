package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.OperationalAlert;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing platform and tenant operational alerts in {@code plat.alerts}.
 */
public interface OperationalAlertRepository {

    /**
     * Creates an operational alert.
     *
     * @param context the security context
     * @param alert   the alert payload
     * @return the created alert
     */
    OperationalAlert createAlert(UserSecurityContext context, OperationalAlert alert);

    /**
     * Finds an alert by primary key.
     *
     * @param context the security context
     * @param id      the alert UUID
     * @return the alert if present
     */
    Optional<OperationalAlert> findById(UserSecurityContext context, UUID id);

    /**
     * Lists alerts filtered optionally by status ("ACTIVE", "ACKNOWLEDGED", "RESOLVED").
     *
     * @param context the security context
     * @param status  optional status filter (null for all active/non-deleted)
     * @return list of alerts
     */
    List<OperationalAlert> listAlerts(UserSecurityContext context, String status);

    /**
     * Acknowledges an alert by updating status to ACKNOWLEDGED.
     *
     * @param context the security context
     * @param id      the alert UUID
     * @return updated alert
     */
    OperationalAlert acknowledgeAlert(UserSecurityContext context, UUID id);

    /**
     * Resolves an alert by updating status to RESOLVED.
     *
     * @param context the security context
     * @param id      the alert UUID
     * @return updated alert
     */
    OperationalAlert resolveAlert(UserSecurityContext context, UUID id);
}
