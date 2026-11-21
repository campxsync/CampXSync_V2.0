package com.campx.iadm.identity.service;

import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.security.UserSecurityContext;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** IADM-03 Platform Identity & Access — keys adm08/adm09. Ceilings = CADM-03 contract. */
public class Iadm03DomainService {
    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> listRoles(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            List<Map<String, Object>> roles = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT code, name FROM iam.roles WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("code", rs.getString(1));
                        r.put("name", rs.getString(2));
                        roles.add(r);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("roles", roles);
            return out;
        });
    }

    public Map<String, Object> setCeiling(UserSecurityContext ctx, String roleCode, String keysCsv) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            UUID id = UUID.randomUUID();
            String[] keys = (keysCsv == null || keysCsv.isEmpty()) ? new String[0] : keysCsv.split(",");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.role_ceilings (id, tenant_id, role_code, allowed_permission_keys, created_by) VALUES (?,?,?,?::text[],?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, roleCode);
                ps.setArray(4, conn.createArrayOf("text", keys));
                ps.setObject(5, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ceilingId", id.toString());
            out.put("roleCode", roleCode);
            return out;
        });
    }

    public Map<String, Object> getCeiling(UserSecurityContext ctx, String roleCode) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.view");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, role_code, allowed_permission_keys FROM iam.role_ceilings " +
                            "WHERE tenant_id=? AND role_code=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, ctx.getTenantId());
                ps.setString(2, roleCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM03_CEILING_NOT_FOUND", "No ceiling for role");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ceilingId", rs.getObject(1).toString());
                    out.put("roleCode", rs.getString(2));
                    Array arr = rs.getArray(3);
                    out.put("allowedPermissionKeys", arr == null ? Collections.emptyList() : Arrays.asList((String[]) arr.getArray()));
                    return out;
                }
            }
        });
    }

    public Map<String, Object> createAccessRequest(UserSecurityContext ctx, String type, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.access_change_requests (id, tenant_id, requested_by, request_type, reason, status, created_by) " +
                            "VALUES (?,?,?,?,?,'PENDING',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, ctx.getUserId());
                ps.setString(4, type);
                ps.setString(5, reason);
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            return out;
        });
    }

    public Map<String, Object> decideAccessRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm09.manage");
            String status = decision != null && decision.toUpperCase().startsWith("APP") ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE iam.access_change_requests SET status=?, decided_by=?, decided_at=now(), decision_note=?, updated_by=? " +
                            "WHERE id=? AND tenant_id=? AND status='PENDING' AND requested_by <> ?")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                ps.setObject(6, ctx.getTenantId());
                ps.setObject(7, ctx.getUserId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM03_REQUEST", "Pending request not found or self-approve blocked");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            return out;
        });
    }
}
