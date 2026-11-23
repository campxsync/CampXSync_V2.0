package com.campx.iadm.config.service;

import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.exception.MalformedPayloadException;
import com.campx.iadm.config.exception.ResourceNotFoundException;
import com.campx.iadm.config.exception.UserProfileAccessDeniedException;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * IADM-02 Platform Configuration domain service.
 * Permission-key (and platform-admin role_code) checks; durable same-TX audit via audit.log_event.
 */
public class ConfigDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ConfigDomainService.class);

    private final DatabaseConnectionManager db;

    public ConfigDomainService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public ConfigDomainService(DatabaseConnectionManager db) {
        this.db = db;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "UP");
        result.put("service", "IADM-02");
        return result;
    }

    public List<Map<String, Object>> listCatalogue(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            Map<String, Object> active = loadActiveVersion(conn);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT setting_key, data_type, default_value::text, description, allowed_scopes "
                            + "FROM cfg.setting_definitions WHERE deleted_at IS NULL ORDER BY setting_key")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        String key = rs.getString("setting_key");
                        row.put("key", key);
                        row.put("dataType", rs.getString("data_type"));
                        row.put("defaultValue", stripJson(rs.getString("default_value")));
                        row.put("description", rs.getString("description"));
                        row.put("domain", domainOf(key));
                        row.put("activeValue", valueFromPayload(active, key));
                        row.put("activeVersionId", active.get("versionId"));
                        row.put("activeVersionLabel", active.get("versionLabel"));
                        out.add(row);
                    }
                }
            }
            return out;
        });
    }

    public Map<String, Object> getCatalogueEntry(UserSecurityContext ctx, String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new MalformedPayloadException("Configuration key is required");
        }
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            Map<String, Object> active = loadActiveVersion(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT setting_key, data_type, default_value::text, description, allowed_scopes, is_secret "
                            + "FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
                ps.setString(1, key.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ResourceNotFoundException("This configuration key is not registered.");
                    }
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("key", rs.getString("setting_key"));
                    row.put("dataType", rs.getString("data_type"));
                    row.put("defaultValue", stripJson(rs.getString("default_value")));
                    row.put("description", rs.getString("description"));
                    row.put("domain", domainOf(key.trim()));
                    row.put("secret", rs.getBoolean("is_secret"));
                    row.put("activeValue", valueFromPayload(active, key.trim()));
                    row.put("activeVersionId", active.get("versionId"));
                    row.put("activeVersionLabel", active.get("versionLabel"));
                    row.put("constraints", boundsMessage(key.trim()));
                    row.put("schemaVersion", Integer.valueOf(1));
                    return row;
                }
            }
        });
    }

    public Map<String, Object> getDashboard(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            Map<String, Object> active = loadActiveVersion(conn);
            out.put("activeVersion", active);
            out.put("pendingDrafts", countDrafts(conn, "DRAFT,VALIDATED,PENDING_APPROVAL,APPROVED"));
            out.put("validationFailures", countDraftsWithErrors(conn));
            out.put("recentPublications", listRecentVersions(conn, 5));
            out.put("schemaVersion", Integer.valueOf(1));
            return out;
        });
    }

    public Map<String, Object> createDraft(UserSecurityContext ctx, String key, String proposedValue,
                                           String reason, String idempotencyKey, String correlationId) {
        if (key == null || key.trim().isEmpty()) {
            throw new MalformedPayloadException("Configuration key is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new MalformedPayloadException("Reason is mandatory");
        }
        final String k = key.trim();
        return ctx.executeInTransaction(db, conn -> {
            requireDraftAuthority(conn, ctx, k);
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                Map<String, Object> existing = findDraftByIdempotency(conn, idempotencyKey.trim());
                if (existing != null) {
                    return existing;
                }
            }
            if (!keyExists(conn, k)) {
                throw new InstituteAdminException(404, "IADM02_KEY_NOT_REGISTERED",
                        "This configuration key is not registered.");
            }
            UUID id = UUID.randomUUID();
            String corr = correlationId != null && !correlationId.trim().isEmpty()
                    ? correlationId.trim() : UUID.randomUUID().toString();
            String jsonVal = toJsonLiteral(proposedValue);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.config_drafts ("
                            + "id, setting_key, proposed_value, reason, status, author_user_id, "
                            + "idempotency_key, correlation_id, created_by) "
                            + "VALUES (?, ?, ?::jsonb, ?, 'DRAFT', ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setString(2, k);
                ps.setString(3, jsonVal);
                ps.setString(4, reason.trim());
                ps.setObject(5, ctx.getUserId());
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                    ps.setString(6, idempotencyKey.trim());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }
                ps.setString(7, corr);
                ps.setObject(8, ctx.getUserId());
                ps.executeUpdate();
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_DRAFT_CREATED", "CONFIG_DRAFT",
                    id.toString(), "Draft created for " + k + ": " + reason.trim());
            return loadDraft(conn, id);
        });
    }

    public Map<String, Object> updateDraft(UserSecurityContext ctx, UUID draftId, String proposedValue, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            Map<String, Object> draft = loadDraft(conn, draftId);
            String status = String.valueOf(draft.get("status"));
            if ("PUBLISHED".equalsIgnoreCase(status) || isTerminalPublished(status)) {
                throw new InstituteAdminException(409, "IADM02_DRAFT_LOCKED",
                        "Published versions never editable; only unpublished drafts can be updated.");
            }
            if ("APPROVED".equalsIgnoreCase(status) || "PENDING_APPROVAL".equalsIgnoreCase(status)) {
                throw new InstituteAdminException(409, "IADM02_DRAFT_LOCKED",
                        "Only unpublished drafts that are not awaiting/approved for publish can be edited.");
            }
            String key = String.valueOf(draft.get("settingKey"));
            requireDraftAuthority(conn, ctx, key);
            String newReason = reason != null && !reason.trim().isEmpty() ? reason.trim() : String.valueOf(draft.get("reason"));
            String jsonVal = proposedValue != null ? toJsonLiteral(proposedValue) : String.valueOf(draft.get("proposedValueRaw"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.config_drafts SET proposed_value = ?::jsonb, reason = ?, status = 'DRAFT', "
                            + "validation_errors = '[]'::jsonb, updated_at = now(), updated_by = ?, "
                            + "row_version = row_version + 1 "
                            + "WHERE id = ? AND deleted_at IS NULL "
                            + "AND status IN ('DRAFT','VALIDATED','REJECTED')")) {
                ps.setString(1, jsonVal);
                ps.setString(2, newReason);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, draftId);
                int n = ps.executeUpdate();
                if (n == 0) {
                    throw new InstituteAdminException(409, "IADM02_CONFLICT",
                            "Concurrent edit conflict or draft not editable.");
                }
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_DRAFT_UPDATED", "CONFIG_DRAFT",
                    draftId.toString(), "Draft updated for " + key);
            return loadDraft(conn, draftId);
        });
    }

    public Map<String, Object> validateDraft(UserSecurityContext ctx, UUID draftId) {
        return ctx.executeInTransaction(db, conn -> {
            Map<String, Object> draft = loadDraft(conn, draftId);
            String key = String.valueOf(draft.get("settingKey"));
            requireDraftAuthority(conn, ctx, key);
            String raw = String.valueOf(draft.get("proposedValue"));
            List<Map<String, String>> errors = validateValue(key, raw);
            String status = errors.isEmpty() ? "VALIDATED" : "DRAFT";
            String errJson = toErrorJson(errors);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.config_drafts SET status = ?, validation_errors = ?::jsonb, "
                            + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                            + "WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setString(2, errJson);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, draftId);
                ps.executeUpdate();
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_DRAFT_VALIDATED", "CONFIG_DRAFT",
                    draftId.toString(), errors.isEmpty() ? "Validation passed" : "Validation failed");
            Map<String, Object> out = loadDraft(conn, draftId);
            out.put("valid", Boolean.valueOf(errors.isEmpty()));
            out.put("fieldErrors", errors);
            return out;
        });
    }

    /** IADM-02-US-016 — submit VALIDATED draft for decision. */
    public Map<String, Object> submitDraft(UserSecurityContext ctx, UUID draftId) {
        return ctx.executeInTransaction(db, conn -> {
            Map<String, Object> draft = loadDraft(conn, draftId);
            String status = String.valueOf(draft.get("status"));
            if ("PENDING_APPROVAL".equalsIgnoreCase(status)) {
                return draft;
            }
            if (!"VALIDATED".equalsIgnoreCase(status)) {
                throw new InstituteAdminException(409, "IADM02_NOT_VALIDATED",
                        "Only validated drafts can be submitted for decision.");
            }
            String key = String.valueOf(draft.get("settingKey"));
            requireDraftAuthority(conn, ctx, key);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.config_drafts SET status = 'PENDING_APPROVAL', "
                            + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                            + "WHERE id = ? AND deleted_at IS NULL AND status = 'VALIDATED'")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, draftId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(409, "IADM02_CONFLICT",
                            "Draft could not be submitted (concurrent change).");
                }
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_DRAFT_SUBMITTED", "CONFIG_DRAFT",
                    draftId.toString(), "Submitted for approval");
            return loadDraft(conn, draftId);
        });
    }

    /** IADM-02-US-017 — approve or reject; drafter cannot decide (D5). */
    public Map<String, Object> decideDraft(UserSecurityContext ctx, UUID draftId, String decision, String reason) {
        if (decision == null || decision.trim().isEmpty()) {
            throw new MalformedPayloadException("Decision (APPROVE or REJECT) is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new MalformedPayloadException("Decision reason is mandatory");
        }
        final String d = decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(d) && !"REJECT".equals(d)) {
            throw new MalformedPayloadException("Decision must be APPROVE or REJECT");
        }
        return ctx.executeInTransaction(db, conn -> {
            requirePermissionOrSuperAdmin(conn, ctx, "cfg.approve");
            Map<String, Object> draft = loadDraft(conn, draftId);
            String status = String.valueOf(draft.get("status"));
            if ("APPROVED".equalsIgnoreCase(status) && "APPROVE".equals(d)) {
                return draft;
            }
            if ("REJECTED".equalsIgnoreCase(status) && "REJECT".equals(d)) {
                return draft;
            }
            if (!"PENDING_APPROVAL".equalsIgnoreCase(status)) {
                throw new InstituteAdminException(409, "IADM02_NOT_PENDING",
                        "Only drafts pending approval can be decided.");
            }
            Object author = draft.get("authorUserId");
            if (author != null && ctx.getUserId() != null
                    && author.toString().equalsIgnoreCase(ctx.getUserId().toString())) {
                // Commit denial audit on a separate connection — the outer TX rolls back on throw.
                recordCommittedDenialAudit(ctx, draftId,
                        "SELF_APPROVE denied: drafter cannot decide own draft");
                throw new InstituteAdminException(403, "IADM02_SELF_APPROVE",
                        "The person who wrote the draft cannot approve or reject it.");
            }
            String next = "APPROVE".equals(d) ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.config_drafts SET status = ?, approver_user_id = ?, decision_reason = ?, "
                            + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                            + "WHERE id = ? AND deleted_at IS NULL AND status = 'PENDING_APPROVAL'")) {
                ps.setString(1, next);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, reason.trim());
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, draftId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(409, "IADM02_CONFLICT", "Draft decision conflict.");
                }
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_DRAFT_DECIDED", "CONFIG_DRAFT",
                    draftId.toString(), next + ": " + reason.trim());
            return loadDraft(conn, draftId);
        });
    }

    /** IADM-02-US-018 — publish approved draft into a new active version. */
    public Map<String, Object> publishDraft(UserSecurityContext ctx, UUID draftId,
                                            String releaseNotes, String idempotencyKey) {
        return ctx.executeInTransaction(db, conn -> {
            requirePermissionOrSuperAdmin(conn, ctx, "cfg.publish");
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                Map<String, Object> existing = findVersionByIdempotency(conn, idempotencyKey.trim());
                if (existing != null) {
                    return existing;
                }
            }
            Map<String, Object> draft = loadDraft(conn, draftId);
            if (!"APPROVED".equalsIgnoreCase(String.valueOf(draft.get("status")))) {
                throw new InstituteAdminException(409, "IADM02_NOT_APPROVED",
                        "Only approved drafts can be published.");
            }
            String key = String.valueOf(draft.get("settingKey"));
            if (key == null || "null".equals(key)) {
                throw new InstituteAdminException(400, "IADM02_POLICY_PUBLISH",
                        "Policy drafts publish is not enabled in this slice.");
            }
            Map<String, Object> active = loadActiveVersion(conn);
            UUID supersedes = active.get("versionId") != null
                    ? UUID.fromString(String.valueOf(active.get("versionId"))) : null;
            String priorJson = active.get("payloadJson") != null
                    ? String.valueOf(active.get("payloadJson")) : "{}";
            String proposed = String.valueOf(draft.get("proposedValue"));
            String newPayload = mergePayload(priorJson, key, proposed);
            String label = nextVersionLabel(conn);
            UUID versionId = UUID.randomUUID();
            String checksum = sha256(newPayload);
            if (supersedes != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE cfg.config_versions SET status = 'SUPERSEDED', updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, supersedes);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.config_versions ("
                            + "id, version_label, status, effective_from, release_notes, published_by, "
                            + "supersedes_version_id, checksum, payload, idempotency_key, correlation_id, created_by) "
                            + "VALUES (?, ?, 'PUBLISHED', now(), ?, ?, ?, ?, ?::jsonb, ?, ?, ?)")) {
                ps.setObject(1, versionId);
                ps.setString(2, label);
                ps.setString(3, releaseNotes != null ? releaseNotes.trim() : "Published from draft " + draftId);
                ps.setObject(4, ctx.getUserId());
                if (supersedes != null) {
                    ps.setObject(5, supersedes);
                } else {
                    ps.setNull(5, Types.OTHER);
                }
                ps.setString(6, checksum);
                ps.setString(7, newPayload);
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                    ps.setString(8, idempotencyKey.trim());
                } else {
                    ps.setNull(8, Types.VARCHAR);
                }
                ps.setString(9, String.valueOf(draft.get("correlationId")));
                ps.setObject(10, ctx.getUserId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.config_drafts SET deleted_at = now(), updated_at = now(), updated_by = ?, "
                            + "decision_reason = coalesce(decision_reason,'') || ' [PUBLISHED ' || ? || ']', "
                            + "row_version = row_version + 1 WHERE id = ?")) {
                ps.setObject(1, ctx.getUserId());
                ps.setString(2, label);
                ps.setObject(3, draftId);
                ps.executeUpdate();
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_VERSION_PUBLISHED", "CONFIG_VERSION",
                    versionId.toString(), "Published " + label + " from draft " + draftId);
            Map<String, Object> out = loadVersion(conn, versionId);
            out.put("published", Boolean.TRUE);
            // IADM-02-US-030 — COM unavailable: non-blocking deferred notification status.
            out.put("notificationDeferred", Boolean.TRUE);
            out.put("notificationMessage", "Action completed; notification delivery is delayed.");
            return out;
        });
    }

    /**
     * IADM-02-US-027 — corrective republish: new immutable version cloned from a prior version
     * (D11 — new version, not pointer flip). History preserved; consumers pull the new active.
     */
    public Map<String, Object> correctiveRepublish(UserSecurityContext ctx, UUID sourceVersionId,
                                                   String reason, String releaseNotes, String idempotencyKey) {
        return ctx.executeInTransaction(db, conn -> {
            assertMutationAllowed(conn, ctx);
            requirePermissionOrSuperAdmin(conn, ctx, "cfg.publish");
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM02_VALIDATION",
                        "Corrective republish requires a reason.");
            }
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                Map<String, Object> existing = findVersionByIdempotency(conn, idempotencyKey.trim());
                if (existing != null) {
                    existing.put("notificationDeferred", Boolean.TRUE);
                    existing.put("notificationMessage", "Action completed; notification delivery is delayed.");
                    return existing;
                }
            }
            Map<String, Object> source = loadVersion(conn, sourceVersionId);
            if (source.get("versionId") == null) {
                throw new ResourceNotFoundException("CONFIG_VERSION", sourceVersionId.toString());
            }
            String sourcePayload = source.get("payload") != null
                    ? String.valueOf(source.get("payload")) : "{}";
            Map<String, Object> active = loadActiveVersion(conn);
            UUID supersedes = active.get("versionId") != null
                    ? UUID.fromString(String.valueOf(active.get("versionId"))) : null;
            if (supersedes != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE cfg.config_versions SET status = 'SUPERSEDED', updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setObject(2, supersedes);
                    ps.executeUpdate();
                }
            }
            String label = nextVersionLabel(conn);
            UUID versionId = UUID.randomUUID();
            String checksum = sha256(sourcePayload);
            String notes = releaseNotes != null && !releaseNotes.trim().isEmpty()
                    ? releaseNotes.trim()
                    : "Corrective republish from " + source.get("versionLabel") + ": " + reason.trim();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.config_versions ("
                            + "id, version_label, status, effective_from, release_notes, published_by, "
                            + "supersedes_version_id, checksum, payload, idempotency_key, correlation_id, created_by) "
                            + "VALUES (?, ?, 'PUBLISHED', now(), ?, ?, ?, ?, ?::jsonb, ?, ?, ?)")) {
                ps.setObject(1, versionId);
                ps.setString(2, label);
                ps.setString(3, notes);
                ps.setObject(4, ctx.getUserId());
                if (supersedes != null) {
                    ps.setObject(5, supersedes);
                } else {
                    ps.setNull(5, Types.OTHER);
                }
                ps.setString(6, checksum);
                ps.setString(7, sourcePayload);
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                    ps.setString(8, idempotencyKey.trim());
                } else {
                    ps.setNull(8, Types.VARCHAR);
                }
                ps.setString(9, "corrective:" + sourceVersionId);
                ps.setObject(10, ctx.getUserId());
                ps.executeUpdate();
            }
            appendDurableAudit(conn, ctx, null, "IADM02_CONFIG_ROLLBACK_PUBLISHED", "CONFIG_VERSION",
                    versionId.toString(),
                    "Corrective " + label + " from source " + source.get("versionLabel")
                            + " (" + sourceVersionId + "): " + reason.trim());
            Map<String, Object> out = loadVersion(conn, versionId);
            out.put("corrective", Boolean.TRUE);
            out.put("restoredFromVersionId", sourceVersionId.toString());
            out.put("restoredFromVersionLabel", source.get("versionLabel"));
            out.put("notificationDeferred", Boolean.TRUE);
            out.put("notificationMessage", "Action completed; notification delivery is delayed.");
            return out;
        });
    }

    /** IADM-02-US-019 — version history. */
    public List<Map<String, Object>> listVersions(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            return listRecentVersions(conn, 50);
        });
    }

    public Map<String, Object> compareVersions(UserSecurityContext ctx, UUID leftId, UUID rightId) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            Map<String, Object> left = loadVersion(conn, leftId);
            Map<String, Object> right = loadVersion(conn, rightId);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("left", left);
            out.put("right", right);
            out.put("schemaVersion", Integer.valueOf(1));
            return out;
        });
    }

    /**
     * IADM-02-US-021…024 — consumer effective-config read.
     * Identity from authenticated user → iam.api_clients; scope from api_client_grants.
     * Never returns drafts. STALE/UNAVAILABLE are computed by the consumer (D10).
     */
    public Map<String, Object> consumerEffectiveConfig(UserSecurityContext ctx, String requestedDomainsCsv) {
        return ctx.executeInTransaction(db, conn -> {
            // iam.api_clients / grants RLS requires adm03.*; cfg.config_versions published
            // SELECT also needs cfg.consumer.read via has_permission (role_assignments).
            // Service consumers authenticate via api_client grant, not role_assignments —
            // elevate only for registry + published payload lookup (same TX, then audit).
            try (Statement elevate = conn.createStatement()) {
                elevate.execute("RESET ROLE");
            }

            Map<String, Object> client = resolveActiveApiClient(conn, ctx);
            String scopeJson = String.valueOf(client.get("resourceScope"));
            Set<String> allowedDomains = parseScopeList(scopeJson, "domains");
            Set<String> allowedKeys = parseScopeList(scopeJson, "keys");
            Set<String> requestedDomains = parseCsvSet(requestedDomainsCsv);

            for (String d : requestedDomains) {
                if (!allowedDomains.contains(d)) {
                    // Separate connection — this TX rolls back on the throw below.
                    recordCommittedConsumerDenialAudit(ctx, String.valueOf(client.get("clientId")),
                            "Denied domain " + d);
                    throw new InstituteAdminException(403, "IADM02_CONSUMER_DOMAIN_DENIED",
                            "Domain '" + d + "' is outside this consumer's approved scope.");
                }
            }

            Map<String, Object> active = loadActiveVersion(conn);
            if (active.get("versionId") == null) {
                throw new InstituteAdminException(503, "IADM02_CONFIG_UNAVAILABLE",
                        "No published configuration version is available.");
            }
            Map<String, Object> keys = new LinkedHashMap<String, Object>();
            for (String key : allowedKeys) {
                String domain = domainOf(key);
                if (!requestedDomains.isEmpty() && !requestedDomains.contains(domain)) {
                    continue;
                }
                if (!allowedDomains.contains(domain)) {
                    continue;
                }
                Object val = valueFromPayload(active, key);
                if (val != null) {
                    keys.put(key, val);
                }
            }
            // Never expose draft material — keys come only from published payload.
            String fetchedAt = java.time.Instant.now().toString();
            ctx.applyClaimsAndRole(conn);
            appendDurableAudit(conn, ctx, null, "IADM02_CONSUMER_READ", "CONFIG_VERSION",
                    String.valueOf(active.get("versionId")),
                    "Consumer " + client.get("clientName") + " fetched " + keys.size() + " keys");

            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("schemaVersion", Integer.valueOf(1));
            out.put("configVersionId", active.get("versionId"));
            out.put("configVersionLabel", active.get("versionLabel"));
            out.put("fetchedAt", fetchedAt);
            out.put("keys", keys);
            out.put("consumerId", client.get("clientId"));
            out.put("consumerName", client.get("clientName"));
            return out;
        });
    }

    /** IADM-02-US-025 — read-only consumer fetch diagnostics from audit.events. */
    public List<Map<String, Object>> listConsumerDiagnostics(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            PlatformRole pr = resolvePlatformRole(conn, ctx);
            if (pr != PlatformRole.SUPER_ADMIN && pr != PlatformRole.SYSTEM_ADMIN) {
                throw new InstituteAdminException(403, "IADM02_FORBIDDEN",
                        "Consumer diagnostics are limited to platform operators.");
            }
            Map<String, Object> active = loadActiveVersion(conn);
            String activeId = active.get("versionId") != null ? String.valueOf(active.get("versionId")) : null;
            // audit.events SELECT RLS is tenant-scoped (audit.read). Platform operators are
            // already authorized above; elevate for cross-tenant consumer telemetry read-only.
            try (Statement elevate = conn.createStatement()) {
                elevate.execute("RESET ROLE");
            }
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT ON (principal_id) principal_id::text, resource_id::text, event_time, "
                            + "detail::text, action "
                            + "FROM audit.events "
                            + "WHERE action = 'IADM02_CONSUMER_READ' AND status = 'SUCCESS' "
                            + "ORDER BY principal_id, event_time DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("principalId", rs.getString("principal_id"));
                        String versionId = rs.getString("resource_id");
                        row.put("lastConfigVersionId", versionId);
                        row.put("lastFetchedAt", rs.getTimestamp("event_time") != null
                                ? rs.getTimestamp("event_time").toInstant().toString() : null);
                        boolean stale = activeId != null && versionId != null && !activeId.equals(versionId);
                        row.put("status", stale ? "STALE" : "FRESH");
                        row.put("activeConfigVersionId", activeId);
                        out.add(row);
                    }
                }
            }
            return out;
        });
    }

    public List<Map<String, Object>> listPolicies(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireAny(conn, ctx, new String[]{"cfg.catalog.view", "cfg.view"}, true);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT policy_code, name, category, rules::text, enforcement_mode "
                            + "FROM cfg.policies WHERE deleted_at IS NULL ORDER BY policy_code")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("policyCode", rs.getString("policy_code"));
                        row.put("name", rs.getString("name"));
                        row.put("category", rs.getString("category"));
                        row.put("rules", rs.getString("rules"));
                        row.put("enforcementMode", rs.getString("enforcement_mode"));
                        out.add(row);
                    }
                }
            }
            return out;
        });
    }

    public Map<String, Object> createPolicyDraft(UserSecurityContext ctx, String policyCode,
                                                 String proposedRules, String reason,
                                                 String idempotencyKey, String correlationId) {
        if (policyCode == null || policyCode.trim().isEmpty()) {
            throw new MalformedPayloadException("Policy code is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new MalformedPayloadException("Reason is mandatory");
        }
        return ctx.executeInTransaction(db, conn -> {
            requirePermissionOrSuperAdmin(conn, ctx, "cfg.policy.draft");
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                Map<String, Object> existing = findDraftByIdempotency(conn, idempotencyKey.trim());
                if (existing != null) {
                    return existing;
                }
            }
            UUID id = UUID.randomUUID();
            String corr = correlationId != null && !correlationId.trim().isEmpty()
                    ? correlationId.trim() : UUID.randomUUID().toString();
            String rulesJson = proposedRules != null ? proposedRules : "{}";
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.config_drafts ("
                            + "id, policy_code, proposed_value, reason, status, author_user_id, "
                            + "idempotency_key, correlation_id, created_by) "
                            + "VALUES (?, ?, ?::jsonb, ?, 'DRAFT', ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setString(2, policyCode.trim());
                ps.setString(3, rulesJson);
                ps.setString(4, reason.trim());
                ps.setObject(5, ctx.getUserId());
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                    ps.setString(6, idempotencyKey.trim());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }
                ps.setString(7, corr);
                ps.setObject(8, ctx.getUserId());
                ps.executeUpdate();
            }
            appendDurableAudit(conn, ctx, null, "IADM02_POLICY_DRAFT_CREATED", "POLICY_DRAFT",
                    id.toString(), "Policy draft for " + policyCode.trim());
            return loadDraft(conn, id);
        });
    }

    public List<Map<String, Object>> listDrafts(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            requireDraftListAuthority(conn, ctx);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, setting_key, policy_code, proposed_value::text, reason, status, "
                            + "validation_errors::text, author_user_id, idempotency_key, correlation_id, created_at "
                            + "FROM cfg.config_drafts WHERE deleted_at IS NULL ORDER BY created_at DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapDraft(rs));
                    }
                }
            }
            return out;
        });
    }

    public Map<String, Object> getDraft(UserSecurityContext ctx, UUID draftId) {
        return ctx.executeInTransaction(db, conn -> {
            requireDraftListAuthority(conn, ctx);
            return loadDraft(conn, draftId);
        });
    }

    // --- authority helpers ---

    private void requireDraftListAuthority(Connection conn, UserSecurityContext ctx) throws SQLException {
        requireAny(conn, ctx, new String[]{"cfg.draft", "cfg.approve", "cfg.publish"}, true);
    }

    private void requireDraftAuthority(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        assertMutationAllowed(conn, ctx);
        PlatformRole pr = resolvePlatformRole(conn, ctx);
        if (pr == PlatformRole.SUPER_ADMIN) {
            return;
        }
        if (pr == PlatformRole.SYSTEM_ADMIN) {
            if (!"security.session_timeout_min".equals(key)) {
                throw new InstituteAdminException(403, "IADM02_R02_CEILING",
                        "System Admin may draft only security.session_timeout_min for now.");
            }
            return;
        }
        try {
            ctx.checkPermission(conn, "cfg.draft");
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM02_FORBIDDEN",
                    "You are not authorized for this configuration action.");
        }
        if (pr == PlatformRole.NONE) {
            // Tenant holders of cfg.draft (SYSTEM_ADMIN role) still limited
            if (!"security.session_timeout_min".equals(key) && hasOnlySystemAdminDraft(conn, ctx)) {
                throw new InstituteAdminException(403, "IADM02_R02_CEILING",
                        "System Admin may draft only security.session_timeout_min for now.");
            }
        }
    }

    private boolean hasOnlySystemAdminDraft(Connection conn, UserSecurityContext ctx) {
        // Best-effort: if not platform admin, treat non-super draft holders under R02 ceiling when key not security.
        return true;
    }

    private void requirePermissionOrSuperAdmin(Connection conn, UserSecurityContext ctx, String permission)
            throws SQLException {
        assertMutationAllowed(conn, ctx);
        PlatformRole pr = resolvePlatformRole(conn, ctx);
        if (pr == PlatformRole.SUPER_ADMIN) {
            return;
        }
        try {
            ctx.checkPermission(conn, permission);
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM02_FORBIDDEN",
                    "You are not authorized for this configuration action.");
        }
    }

    /**
     * US-028 / US-029 — auditors and institute/college admins cannot mutate platform config.
     */
    private void assertMutationAllowed(Connection conn, UserSecurityContext ctx) throws SQLException {
        String tenantRole = resolveTenantBusinessRole(conn, ctx);
        if ("AUDITOR".equals(tenantRole) || "R28".equals(tenantRole)) {
            throw new InstituteAdminException(403, "IADM02_AUDITOR_READONLY",
                    "Auditor access is read-only");
        }
        if ("INSTITUTION_ADMIN".equals(tenantRole) || "R03".equals(tenantRole)
                || "COLLEGE_ADMIN".equals(tenantRole) || "R04".equals(tenantRole)
                || "ADMIN".equals(tenantRole)) {
            throw new InstituteAdminException(403, "IADM02_TENANT_ADMIN_DENIED",
                    "Institution Admin and College Admin cannot change platform defaults or policy.");
        }
    }

    private String resolveTenantBusinessRole(Connection conn, UserSecurityContext ctx) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT upper(r.code) FROM iam.role_assignments ra "
                        + "JOIN iam.roles r ON r.id = ra.role_id "
                        + "WHERE ra.user_id = ? AND ra.deleted_at IS NULL "
                        + "ORDER BY ra.created_at DESC NULLS LAST LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private void requireAny(Connection conn, UserSecurityContext ctx, String[] permissions, boolean allowPlatform)
            throws SQLException {
        if (allowPlatform) {
            PlatformRole pr = resolvePlatformRole(conn, ctx);
            if (pr == PlatformRole.SUPER_ADMIN || pr == PlatformRole.SYSTEM_ADMIN) {
                return;
            }
        }
        UserProfileAccessDeniedException last = null;
        for (int i = 0; i < permissions.length; i++) {
            try {
                ctx.checkPermission(conn, permissions[i]);
                return;
            } catch (UserProfileAccessDeniedException ex) {
                last = ex;
            }
        }
        throw new InstituteAdminException(403, "IADM02_FORBIDDEN",
                "You are not authorized for this configuration action."
                        + (last != null ? "" : ""));
    }

    private Map<String, Object> resolveActiveApiClient(Connection conn, UserSecurityContext ctx)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.id, c.name, g.resource_scope::text "
                        + "FROM iam.api_clients c "
                        + "JOIN iam.api_client_grants g ON g.client_id = c.id AND g.deleted_at IS NULL "
                        + "JOIN iam.permissions p ON p.id = g.permission_id AND p.deleted_at IS NULL "
                        + "WHERE c.user_id = ? AND c.status = 'ACTIVE' AND c.deleted_at IS NULL "
                        + "AND p.code = 'cfg.consumer.read' "
                        + "AND (g.valid_to IS NULL OR g.valid_to > now()) "
                        + "AND (c.expires_at IS NULL OR c.expires_at > now()) "
                        + "ORDER BY g.created_at DESC LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(403, "IADM02_CONSUMER_FORBIDDEN",
                            "No active consumer grant for cfg.consumer.read.");
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("clientId", rs.getObject("id").toString());
                m.put("clientName", rs.getString("name"));
                m.put("resourceScope", rs.getString("resource_scope"));
                return m;
            }
        }
    }

    private static Set<String> parseScopeList(String scopeJson, String field) {
        Set<String> out = new HashSet<String>();
        if (scopeJson == null) {
            return out;
        }
        String needle = "\"" + field + "\"";
        int i = scopeJson.indexOf(needle);
        if (i < 0) {
            return out;
        }
        int lb = scopeJson.indexOf('[', i);
        int rb = scopeJson.indexOf(']', lb);
        if (lb < 0 || rb < 0) {
            return out;
        }
        String inner = scopeJson.substring(lb + 1, rb);
        String[] parts = inner.split(",");
        for (String part : parts) {
            String s = part.trim().replace("\"", "");
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static Set<String> parseCsvSet(String csv) {
        Set<String> out = new HashSet<String>();
        if (csv == null || csv.trim().isEmpty()) {
            return out;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private enum PlatformRole { SUPER_ADMIN, SYSTEM_ADMIN, NONE }

    private PlatformRole resolvePlatformRole(Connection conn, UserSecurityContext ctx) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT role_code FROM plat.platform_admins "
                        + "WHERE user_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, ctx.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PlatformRole.NONE;
                }
                String code = rs.getString(1);
                if ("SUPER_ADMIN".equalsIgnoreCase(code) || "PLATFORM_ADMIN".equalsIgnoreCase(code)
                        || "R01".equalsIgnoreCase(code)) {
                    return PlatformRole.SUPER_ADMIN;
                }
                if ("SYSTEM_ADMIN".equalsIgnoreCase(code) || "R02".equalsIgnoreCase(code)) {
                    return PlatformRole.SYSTEM_ADMIN;
                }
                return PlatformRole.SUPER_ADMIN; // other platform_admins treated as elevated
            }
        }
    }

    // --- persistence helpers ---

    private Map<String, Object> loadActiveVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, version_label, payload::text, effective_from, checksum "
                        + "FROM cfg.config_versions WHERE status = 'PUBLISHED' AND deleted_at IS NULL "
                        + "ORDER BY effective_from DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                if (!rs.next()) {
                    m.put("versionId", null);
                    m.put("versionLabel", null);
                    m.put("payload", Collections.emptyMap());
                    return m;
                }
                m.put("versionId", rs.getObject("id").toString());
                m.put("versionLabel", rs.getString("version_label"));
                m.put("payloadJson", rs.getString("payload"));
                m.put("effectiveFrom", rs.getTimestamp("effective_from") != null
                        ? rs.getTimestamp("effective_from").toInstant().toString() : null);
                m.put("checksum", rs.getString("checksum"));
                return m;
            }
        }
    }

    private Object valueFromPayload(Map<String, Object> active, String key) {
        String json = active == null ? null : (String) active.get("payloadJson");
        if (json == null) {
            return null;
        }
        // Minimal extract: "key": value
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        }
        int end = start;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private boolean keyExists(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<String, Object> findDraftByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM cfg.config_drafts WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return loadDraft(conn, (UUID) rs.getObject(1));
            }
        }
    }

    private Map<String, Object> loadDraft(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, setting_key, policy_code, proposed_value::text, reason, status, "
                        + "validation_errors::text, author_user_id, idempotency_key, correlation_id, created_at "
                        + "FROM cfg.config_drafts WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Draft not found");
                }
                return mapDraft(rs);
            }
        }
    }

    private Map<String, Object> mapDraft(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("draftId", rs.getObject("id").toString());
        row.put("settingKey", rs.getString("setting_key"));
        row.put("policyCode", rs.getString("policy_code"));
        String proposed = stripJson(rs.getString("proposed_value"));
        row.put("proposedValue", proposed);
        row.put("proposedValueRaw", rs.getString("proposed_value"));
        row.put("reason", rs.getString("reason"));
        row.put("status", rs.getString("status"));
        row.put("validationErrors", rs.getString("validation_errors"));
        row.put("authorUserId", rs.getObject("author_user_id") != null
                ? rs.getObject("author_user_id").toString() : null);
        row.put("idempotencyKey", rs.getString("idempotency_key"));
        row.put("correlationId", rs.getString("correlation_id"));
        row.put("createdAt", rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant().toString() : null);
        row.put("schemaVersion", Integer.valueOf(1));
        return row;
    }

    private int countDrafts(Connection conn, String statusesCsv) throws SQLException {
        String[] parts = statusesCsv.split(",");
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                in.append(',');
            }
            in.append('?');
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM cfg.config_drafts WHERE deleted_at IS NULL AND status IN (" + in + ")")) {
            for (int i = 0; i < parts.length; i++) {
                ps.setString(i + 1, parts[i].trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countDraftsWithErrors(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM cfg.config_drafts WHERE deleted_at IS NULL "
                        + "AND validation_errors IS NOT NULL AND validation_errors::text <> '[]'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<Map<String, Object>> listRecentVersions(Connection conn, int limit) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, version_label, status, effective_from, release_notes "
                        + "FROM cfg.config_versions WHERE deleted_at IS NULL "
                        + "ORDER BY effective_from DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("versionId", rs.getObject("id").toString());
                    m.put("versionLabel", rs.getString("version_label"));
                    m.put("status", rs.getString("status"));
                    m.put("effectiveFrom", rs.getTimestamp("effective_from") != null
                            ? rs.getTimestamp("effective_from").toInstant().toString() : null);
                    m.put("releaseNotes", rs.getString("release_notes"));
                    out.add(m);
                }
            }
        }
        return out;
    }

    private Map<String, Object> findVersionByIdempotency(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM cfg.config_versions WHERE idempotency_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return loadVersion(conn, (UUID) rs.getObject(1));
            }
        }
    }

    private Map<String, Object> loadVersion(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, version_label, status, effective_from, release_notes, payload::text, "
                        + "checksum, published_by, supersedes_version_id "
                        + "FROM cfg.config_versions WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("Version not found");
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("versionId", rs.getObject("id").toString());
                m.put("versionLabel", rs.getString("version_label"));
                m.put("status", rs.getString("status"));
                m.put("effectiveFrom", rs.getTimestamp("effective_from") != null
                        ? rs.getTimestamp("effective_from").toInstant().toString() : null);
                m.put("releaseNotes", rs.getString("release_notes"));
                m.put("payload", rs.getString("payload"));
                m.put("checksum", rs.getString("checksum"));
                m.put("publishedBy", rs.getObject("published_by") != null
                        ? rs.getObject("published_by").toString() : null);
                m.put("supersedesVersionId", rs.getObject("supersedes_version_id") != null
                        ? rs.getObject("supersedes_version_id").toString() : null);
                m.put("schemaVersion", Integer.valueOf(1));
                return m;
            }
        }
    }

    private String nextVersionLabel(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT version_label FROM cfg.config_versions WHERE deleted_at IS NULL "
                        + "ORDER BY created_at DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                int n = 1;
                if (rs.next()) {
                    String label = rs.getString(1);
                    if (label != null && label.startsWith("VER-")) {
                        try {
                            n = Integer.parseInt(label.substring(4)) + 1;
                        } catch (NumberFormatException ignored) {
                            n = 2;
                        }
                    }
                }
                return String.format(Locale.ROOT, "VER-%03d", Integer.valueOf(n));
            }
        }
    }

    /**
     * Replace or insert a single key in a JSON object payload (numeric or string values).
     */
    static String mergePayload(String priorJson, String key, String proposedValue) {
        String base = priorJson == null || priorJson.trim().isEmpty() ? "{}" : priorJson.trim();
        if (!base.startsWith("{")) {
            base = "{}";
        }
        String quotedKey = "\"" + key + "\":";
        String valueLiteral;
        Integer n = parseInt(proposedValue);
        if (n != null) {
            valueLiteral = String.valueOf(n);
        } else {
            valueLiteral = "\"" + escape(proposedValue == null ? "" : proposedValue) + "\"";
        }
        int idx = base.indexOf(quotedKey);
        if (idx >= 0) {
            int start = idx + quotedKey.length();
            while (start < base.length() && Character.isWhitespace(base.charAt(start))) {
                start++;
            }
            int end = start;
            if (start < base.length() && base.charAt(start) == '"') {
                end = start + 1;
                while (end < base.length() && base.charAt(end) != '"') {
                    if (base.charAt(end) == '\\') {
                        end++;
                    }
                    end++;
                }
                if (end < base.length()) {
                    end++;
                }
            } else {
                while (end < base.length() && ",}]".indexOf(base.charAt(end)) < 0) {
                    end++;
                }
            }
            return base.substring(0, idx) + quotedKey + valueLiteral + base.substring(end);
        }
        // insert before closing brace
        int close = base.lastIndexOf('}');
        if (close < 0) {
            return "{\"" + key + "\":" + valueLiteral + "}";
        }
        String inner = base.substring(1, close).trim();
        if (inner.isEmpty()) {
            return "{\"" + key + "\":" + valueLiteral + "}";
        }
        return "{" + inner + ",\"" + key + "\":" + valueLiteral + "}";
    }

    /**
     * Writes a denial audit that survives the caller's rolled-back transaction.
     */
    private void recordCommittedDenialAudit(UserSecurityContext ctx, UUID draftId, String detail) {
        try (Connection auditConn = db.getConnection()) {
            auditConn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(auditConn);
                appendDurableAudit(auditConn, ctx, null, "IADM02_CONFIG_DRAFT_DECIDE_DENIED", "CONFIG_DRAFT",
                        draftId.toString(), detail, "DENIED");
                auditConn.commit();
            } catch (Exception e) {
                try {
                    auditConn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
                logger.warn("Denial audit write failed for draft {}: {}", draftId, e.getMessage());
            } finally {
                try {
                    auditConn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
        } catch (SQLException e) {
            logger.warn("Denial audit connection failed for draft {}: {}", draftId, e.getMessage());
        }
    }

    private void recordCommittedConsumerDenialAudit(UserSecurityContext ctx, String clientId, String detail) {
        try (Connection auditConn = db.getConnection()) {
            auditConn.setAutoCommit(false);
            try {
                try (Statement elevate = auditConn.createStatement()) {
                    elevate.execute("RESET ROLE");
                }
                appendDurableAudit(auditConn, ctx, null, "IADM02_CONSUMER_READ_DENIED", "API_CLIENT",
                        clientId, detail, "DENIED");
                auditConn.commit();
            } catch (Exception e) {
                try {
                    auditConn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
                logger.warn("Consumer denial audit failed for client {}: {}", clientId, e.getMessage());
            } finally {
                try {
                    auditConn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
        } catch (SQLException e) {
            logger.warn("Consumer denial audit connection failed: {}", e.getMessage());
        }
    }

    private void appendDurableAudit(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                    String action, String resourceType, String resourceId, String detail)
            throws SQLException {
        appendDurableAudit(conn, ctx, tenantId, action, resourceType, resourceId, detail, "SUCCESS");
    }

    private void appendDurableAudit(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                    String action, String resourceType, String resourceId, String detail,
                                    String status)
            throws SQLException {
        String detailJson = "{\"message\":\"" + escape(detail) + "\"}";
        UUID resourceUuid = null;
        if (resourceId != null) {
            try {
                resourceUuid = UUID.fromString(resourceId.trim());
            } catch (IllegalArgumentException ignored) {
                resourceUuid = null;
            }
        }
        String auditStatus = status != null ? status : "SUCCESS";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, ?, 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx.getUserId());
            ps.setString(3, action);
            ps.setString(4, resourceType);
            ps.setObject(5, resourceUuid);
            ps.setString(6, auditStatus);
            ps.setString(7, detailJson);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject(1) == null) {
                    throw new SQLException("audit.log_event returned no id — mutation cannot succeed without durable audit");
                }
            }
        }
    }

    // --- validation ---

    public static List<Map<String, String>> validateValue(String key, String raw) {
        List<Map<String, String>> errors = new ArrayList<Map<String, String>>();
        Integer n = parseInt(raw);
        if (n == null) {
            errors.add(fieldError("proposedValue", "The value does not match the required type."));
            return errors;
        }
        int min = 0;
        int max = 100;
        if ("security.session_timeout_min".equals(key)) {
            min = 1;
            max = 1440;
        } else if ("attendance.min_pct".equals(key) || "exam.grace_marks_max".equals(key)) {
            min = 0;
            max = 100;
        } else if ("fees.late_fee_grace_days".equals(key)) {
            min = 0;
            max = 365;
        } else if ("iam.delegation.max_validity_days".equals(key)) {
            min = 1;
            max = 365;
        }
        if (n.intValue() < min || n.intValue() > max) {
            errors.add(fieldError("proposedValue",
                    "The value does not match the required type or bounds (" + min + ".." + max + ")."));
        }
        return errors;
    }

    private static Map<String, String> fieldError(String field, String message) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("field", field);
        m.put("message", message);
        return m;
    }

    private static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String boundsMessage(String key) {
        List<Map<String, String>> probe = validateValue(key, "0");
        if ("security.session_timeout_min".equals(key)) {
            return "INTEGER 1..1440";
        }
        if ("iam.delegation.max_validity_days".equals(key)) {
            return "INTEGER 1..365";
        }
        if ("fees.late_fee_grace_days".equals(key)) {
            return "INTEGER 0..365";
        }
        return "INTEGER 0..100";
    }

    private static String domainOf(String key) {
        int dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "platform";
    }

    private static String stripJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String toJsonLiteral(String value) {
        if (value == null) {
            return "null";
        }
        String s = value.trim();
        if (s.startsWith("{") || s.startsWith("[")) {
            return s;
        }
        Integer n = parseInt(s);
        if (n != null) {
            return String.valueOf(n);
        }
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            return s.toLowerCase(Locale.ROOT);
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String toErrorJson(List<Map<String, String>> errors) {
        if (errors == null || errors.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Map<String, String> e = errors.get(i);
            sb.append("{\"field\":\"").append(escape(e.get("field")))
                    .append("\",\"message\":\"").append(escape(e.get("message"))).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean isTerminalPublished(String status) {
        return false;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SuppressWarnings("unused")
    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dig.length; i++) {
                sb.append(String.format("%02x", dig[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return md5Fallback(s);
        }
    }

    private static String md5Fallback(String s) {
        return Integer.toHexString(s.hashCode());
    }
}
