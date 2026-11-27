package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03-US-011 — Register a new service's permission keys through an additive
 * manifest.
 * Never applies a migration, removes/renames a key, or touches legacy keys.
 */
public class ServiceRegistrationService {

    public Map<String, Object> list(UserSecurityContext ctx) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R02".equals(persona) || "R28".equals(persona))) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot view service registrations.");
                }
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, service_code, owner_user_id, key_manifest::text, role_grant_proposal::text, " +
                                "holder_impact::text, migration_ref, rollback_ref, test_evidence_ref, audit_evidence_ref, "
                                +
                                "status, validation_notes, decision_reason, created_at " +
                                "FROM iadm03.service_registrations ORDER BY created_at DESC LIMIT 50");
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<String, Object>();
                        m.put("id", String.valueOf(rs.getObject("id")));
                        m.put("serviceCode", rs.getString("service_code"));
                        m.put("ownerUserId", String.valueOf(rs.getObject("owner_user_id")));
                        m.put("keyManifest", rs.getString("key_manifest"));
                        m.put("roleGrantProposal", rs.getString("role_grant_proposal"));
                        m.put("migrationRef", rs.getString("migration_ref"));
                        m.put("rollbackRef", rs.getString("rollback_ref"));
                        m.put("testEvidenceRef", rs.getString("test_evidence_ref"));
                        m.put("auditEvidenceRef", rs.getString("audit_evidence_ref"));
                        m.put("status", rs.getString("status"));
                        m.put("validationNotes", rs.getString("validation_notes"));
                        m.put("decisionReason", rs.getString("decision_reason"));
                        items.add(m);
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("count", items.size());
                out.put("items", items);
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to list service registrations.");
        }
    }

    public Map<String, Object> submit(UserSecurityContext ctx,
            String serviceCode,
            String keyManifestJson,
            String roleGrantProposalJson,
            String migrationRef,
            String rollbackRef,
            String testEvidenceRef,
            String auditEvidenceRef,
            String correlationId) {
        if (serviceCode == null || serviceCode.trim().isEmpty()
                || keyManifestJson == null || keyManifestJson.trim().isEmpty()
                || migrationRef == null || migrationRef.trim().isEmpty()
                || rollbackRef == null || rollbackRef.trim().isEmpty()
                || testEvidenceRef == null || testEvidenceRef.trim().isEmpty()
                || auditEvidenceRef == null || auditEvidenceRef.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "serviceCode, keyManifest, migrationRef, rollbackRef, testEvidenceRef, and auditEvidenceRef are required.");
        }
        String lower = keyManifestJson.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(?<![a-z])adm[0-9].*") || lower.contains("delete") || lower.contains("drop") || lower.contains("*")) {
            throw new InstituteAdminException(400, "IADM03_ADDITIVE_ONLY_VIOLATION",
                    "Service registration manifest must be additive-only; wildcards and legacy adm* mutations are forbidden.");
        }
        String manifestJson = keyManifestJson.trim();
        if (!manifestJson.startsWith("[") && !manifestJson.startsWith("{")) {
            manifestJson = "[\"" + manifestJson.replace("\"", "\\\"") + "\"]";
        }
        String proposalJson = (roleGrantProposalJson == null || roleGrantProposalJson.trim().isEmpty())
                ? "{}"
                : roleGrantProposalJson.trim();
        if (!proposalJson.startsWith("{") && !proposalJson.startsWith("[")) {
            proposalJson = "{\"note\":\"" + proposalJson.replace("\"", "\\\"") + "\"}";
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.registration.submit")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_REGISTRATION_DENIED", "PLATFORM", "SERVICE_REGISTRATION", null,
                            serviceCode, "403", correlationId, "Registration submit denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot submit service registrations.");
                }

                UUID regId = UUID.randomUUID();
                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, null,
                        "IADM03_REGISTRATION_SUBMITTED", "PLATFORM", "SERVICE_REGISTRATION", regId.toString(),
                        null, serviceCode.trim(), correlationId,
                        "Additive service permission manifest submitted");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iadm03.service_registrations " +
                                "(id, service_code, owner_user_id, key_manifest, role_grant_proposal, migration_ref, " +
                                "rollback_ref, test_evidence_ref, audit_evidence_ref, status, audit_event_id) " +
                                "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, 'SUBMITTED', ?)")) {
                    ps.setObject(1, regId);
                    ps.setString(2, serviceCode.trim().toUpperCase(Locale.ROOT));
                    ps.setObject(3, ctx.getUserId());
                    ps.setString(4, manifestJson);
                    ps.setString(5, proposalJson);
                    ps.setString(6, migrationRef.trim());
                    ps.setString(7, rollbackRef.trim());
                    ps.setString(8, testEvidenceRef.trim());
                    ps.setString(9, auditEvidenceRef.trim());
                    ps.setObject(10, auditId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", regId.toString());
                out.put("serviceCode", serviceCode.trim().toUpperCase(Locale.ROOT));
                out.put("status", "SUBMITTED");
                out.put("additiveOnly", Boolean.TRUE);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to submit service registration.");
        }
    }

    public Map<String, Object> validate(UserSecurityContext ctx, UUID regId, String notes, String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R02".equals(persona))) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Only R01 or R02 may validate service registrations.");
                }
                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, null,
                        "IADM03_REGISTRATION_VALIDATED", "PLATFORM", "SERVICE_REGISTRATION", regId.toString(),
                        "SUBMITTED", "VALIDATED", correlationId, "Additive manifest validated");
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.service_registrations SET status = 'VALIDATED', validated_by = ?, " +
                                "validated_at = now(), validation_notes = ? WHERE id = ?")) {
                    ps.setObject(1, ctx.getUserId());
                    ps.setString(2, notes == null ? "Additive naming and evidence checks passed" : notes.trim());
                    ps.setObject(3, regId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Service registration not found.");
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", regId.toString());
                out.put("status", "VALIDATED");
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to validate service registration.");
        }
    }

    public Map<String, Object> decide(UserSecurityContext ctx, UUID regId, String decisionRaw,
            String reason, String correlationId) {
        if (decisionRaw == null || reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision and reason are required.");
        }
        String d = decisionRaw.trim().toUpperCase(Locale.ROOT);
        boolean approve = "APPROVE".equals(d) || "APPROVED".equals(d);
        String finalStatus = approve ? "APPROVED" : "REJECTED";

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.registration.approve")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, null,
                            "IADM03_REGISTRATION_APPROVE_DENIED", "PLATFORM", "SERVICE_REGISTRATION", regId.toString(),
                            finalStatus, "403", correlationId, "Registration approve denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Only Super Admin (R01) may approve service registrations.");
                }
                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, null,
                        "IADM03_REGISTRATION_DECIDED", "PLATFORM", "SERVICE_REGISTRATION", regId.toString(),
                        "VALIDATED", finalStatus, correlationId, "Service registration " + finalStatus);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.service_registrations SET status = ?, decided_by = ?, " +
                                "decided_at = now(), decision_reason = ? WHERE id = ?")) {
                    ps.setString(1, finalStatus);
                    ps.setObject(2, ctx.getUserId());
                    ps.setString(3, reason.trim());
                    ps.setObject(4, regId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Service registration not found.");
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", regId.toString());
                out.put("status", finalStatus);
                out.put("appliedMigration", Boolean.FALSE);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to decide service registration.");
        }
    }
}
