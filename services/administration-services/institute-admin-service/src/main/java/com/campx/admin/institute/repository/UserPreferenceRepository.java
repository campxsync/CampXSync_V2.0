package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.preference.UserPreferences;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.audit.PostgresAuditRepository;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.TransactionalOutboxWriter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for authoritative User Preference management.
 * <p>
 * Reuses existing persistent storage in {@code iam.user_profiles.preferences} (jsonb).
 * Guarantees:
 * <ul>
 *   <li>Zero duplicate tables or divergent schema paths.</li>
 *   <li>Transactional boundary integrity under the caller's verified identity.</li>
 *   <li>Deep-merge preservation of role-scoped widget configs without clobbering.</li>
 *   <li>Preservation of unrelated profile fields (full_name, email, status, college_id).</li>
 *   <li>Shared Services integration: atomic write to {@code audit.events} and {@code sys.outbox_events}.</li>
 *   <li>Intelligence Layer publication of {@link IntelligenceContextEvent} (v1.0.0).</li>
 * </ul>
 * </p>
 */
public class UserPreferenceRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserPreferenceRepository.class);

    private final DatabaseConnectionManager connectionManager;
    private final AuditLogRepository auditRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ControlledIntelligenceAdapter intelligenceAdapter;

    public UserPreferenceRepository() {
        this(DatabaseConnectionManager.getInstance(),
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public UserPreferenceRepository(DatabaseConnectionManager connectionManager) {
        this(connectionManager,
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public UserPreferenceRepository(DatabaseConnectionManager connectionManager,
                                  AuditLogRepository auditRepository,
                                  TransactionalOutboxWriter outboxWriter,
                                  ControlledIntelligenceAdapter intelligenceAdapter) {
        this.connectionManager = connectionManager;
        this.auditRepository = auditRepository;
        this.outboxWriter = outboxWriter;
        this.intelligenceAdapter = intelligenceAdapter != null ? intelligenceAdapter : new ControlledIntelligenceAdapter();
    }

    public AuditLogRepository getAuditRepository() {
        return auditRepository;
    }

    public TransactionalOutboxWriter getOutboxWriter() {
        return outboxWriter;
    }

    public ControlledIntelligenceAdapter getIntelligenceAdapter() {
        return intelligenceAdapter;
    }

    /**
     * Retrieves the authoritative user preferences for the caller identity from iam.user_profiles.preferences.
     *
     * @param context       authenticated security context
     * @param requestedRole role context for resolving effective dashboard widgets
     * @return {@link UserPreferences}
     */
    public UserPreferences getPreferences(UserSecurityContext context, String requestedRole) {
        if (context == null || context.getUserId() == null) {
            throw new UserProfileAccessDeniedException("Authenticated user context is required to retrieve preferences");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, preferences, row_version FROM iam.user_profiles WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID rowTenantId = (UUID) rs.getObject("tenant_id");
                        // Tenant isolation: non-platform callers can only access their own tenant's profile
                        if (!context.isPlatformScope() && context.getTenantId() != null) {
                            if (rowTenantId != null && !context.getTenantId().equals(rowTenantId)) {
                                logger.warn("Cross-tenant preference access blocked: caller tenant [{}] attempted to access profile tenant [{}]",
                                        context.getTenantId(), rowTenantId);
                                throw new UserProfileAccessDeniedException("Cross-tenant access violation");
                            }
                        }

                        String prefsJson = rs.getString("preferences");
                        return UserPreferences.fromJson(prefsJson, requestedRole);
                    }
                }
            }
            throw new UserProfileNotFoundException(context.getUserId().toString(),
                    context.getTenantId() != null ? context.getTenantId().toString() : "platform");
        });
    }

    /**
     * Atomically deep-merges and updates user preferences in iam.user_profiles.preferences,
     * writes an audit event, enqueues an outbox event, and notifies the Intelligence Layer.
     *
     * @param context    authenticated security context
     * @param incoming   new preference values submitted by the client
     * @param activeRole effective role being updated
     * @return updated and saved {@link UserPreferences}
     */
    public UserPreferences updatePreferences(UserSecurityContext context, UserPreferences incoming, String activeRole) {
        if (context == null || context.getUserId() == null) {
            throw new UserProfileAccessDeniedException("Authenticated user context is required to update preferences");
        }
        if (incoming == null) {
            throw new MalformedPayloadException("Incoming preference payload cannot be null");
        }

        // Validate schema constraints
        incoming.validate();

        String normRole = (activeRole != null && !activeRole.trim().isEmpty())
                ? activeRole.trim().toLowerCase(Locale.ROOT)
                : "college_admin";

        return context.executeInTransaction(connectionManager, conn -> {
            // 1. Fetch current profile to ensure existence, tenant ownership, and load existing preferences for deep-merge
            String selectSql = "SELECT id, tenant_id, preferences, row_version FROM iam.user_profiles WHERE id = ? AND deleted_at IS NULL";
            String existingPrefsJson = null;
            UUID rowTenantId = null;

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setObject(1, context.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rowTenantId = (UUID) rs.getObject("tenant_id");
                        // Tenant isolation enforcement
                        if (!context.isPlatformScope() && context.getTenantId() != null) {
                            if (rowTenantId != null && !context.getTenantId().equals(rowTenantId)) {
                                logger.warn("Cross-tenant preference mutation blocked: caller tenant [{}] attempted to update profile tenant [{}]",
                                        context.getTenantId(), rowTenantId);
                                throw new UserProfileAccessDeniedException("Cross-tenant access violation");
                            }
                        }
                        existingPrefsJson = rs.getString("preferences");
                    } else {
                        throw new UserProfileNotFoundException(context.getUserId().toString(),
                                context.getTenantId() != null ? context.getTenantId().toString() : "platform");
                    }
                }
            }

            // 2. Perform deep-merge to preserve non-active role configurations and validate
            UserPreferences currentPrefs = UserPreferences.fromJson(existingPrefsJson, normRole);
            UserPreferences merged = currentPrefs.deepMerge(incoming, normRole);
            String mergedJson = merged.toJson();

            // 3. Persist updated preferences to iam.user_profiles (preserving all unrelated columns)
            String updateSql = "UPDATE iam.user_profiles "
                    + "SET preferences = ?::jsonb, "
                    + "    row_version = row_version + 1, "
                    + "    updated_at = NOW() "
                    + "WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, mergedJson);
                ps.setObject(2, context.getUserId());
                int rowsUpdated = ps.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new UserProfileNotFoundException("Failed to update preferences: user profile not found or deleted");
                }
            }

            UUID effectiveTenantId = context.getTenantId() != null ? context.getTenantId() : rowTenantId;

            // 4. Shared Services Audit Log (audit.events) within the active transaction
            if (auditRepository != null) {
                AuditRecord auditRecord = AuditRecord.builder()
                        .action("USER_PREFERENCE_UPDATED")
                        .tenantId(effectiveTenantId)
                        .principalId(context.getUserId())
                        .principalType("USER")
                        .resourceType("USER_PREFERENCES")
                        .resourceId(context.getUserId())
                        .status("SUCCESS")
                        .severity("INFO")
                        .detailJson("{\"action\":\"PREFERENCE_UPDATED\",\"role\":\"" + normRole + "\"}")
                        .build();
                try {
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for preference update: {}", e.getMessage());
                }
            }

            // 5. Shared Services Transactional Outbox (sys.outbox_events) within the active transaction
            if (outboxWriter != null && effectiveTenantId != null) {
                try {
                    outboxWriter.writeEvent(
                            conn,
                            effectiveTenantId,
                            "institute-admin-service",
                            "USER_PROFILE",
                            context.getUserId(),
                            "UserPreferenceUpdated",
                            mergedJson
                    );
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for preference update: {}", e.getMessage());
                }
            }

            // 6. Intelligence Layer contract emission (v1.0.0) via Controlled Adapter
            if (intelligenceAdapter != null) {
                try {
                    IntelligenceContextEvent intelEvent = IntelligenceContextEvent.builder()
                            .eventType("UserPreferenceUpdated")
                            .aggregateType("USER_PROFILE")
                            .aggregateId(context.getUserId())
                            .tenantId(effectiveTenantId)
                            .actorId(context.getUserId().toString())
                            .sourceService("institute-admin-service")
                            .payloadJson(mergedJson)
                            .build();
                    intelligenceAdapter.publish(intelEvent);
                } catch (Exception e) {
                    logger.warn("Intelligence Layer contract emission encountered error: {}", e.getMessage());
                }
            }

            logger.info("Successfully updated and persisted preferences for user [{}] role [{}] in tenant [{}]",
                    context.getUserId(), normRole, effectiveTenantId);
            return merged;
        });
    }
}
