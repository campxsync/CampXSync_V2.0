package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-03-US-033 — read-only access audit evidence for Auditor (R28) and authorized admins.
 */
public class AuditEvidenceService {

    private final DatabaseConnectionManager db;

    public AuditEvidenceService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public AuditEvidenceService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    public Map<String, Object> listEvidence(final UserSecurityContext ctx, final int limit) {
        final int lim = limit <= 0 ? 50 : Math.min(limit, 200);
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                try {
                    ctx.checkPermission(conn, "iam.audit.view");
                } catch (UserProfileAccessDeniedException ex) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Caller lacks required permission 'iam.audit.view'.");
                }
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id::text, tenant_id::text, action, principal_id::text, resource_type, "
                                + "resource_id::text, status, severity, detail::text, created_at "
                                + "FROM audit.list_iadm03_evidence(?)")) {
                    ps.setInt(1, lim);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getString(1));
                            row.put("tenantId", rs.getString(2));
                            row.put("action", rs.getString(3));
                            row.put("principalId", rs.getString(4));
                            row.put("resourceType", rs.getString(5));
                            row.put("resourceId", rs.getString(6));
                            row.put("status", rs.getString(7));
                            row.put("severity", rs.getString(8));
                            row.put("detail", rs.getString(9));
                            row.put("createdAt", rs.getTimestamp(10) == null
                                    ? null : rs.getTimestamp(10).toInstant().toString());
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("readOnly", Boolean.TRUE);
                out.put("note", "Auditor (R28) evidence is read-only; mutations are denied.");
                return out;
            }
        });
    }
}
