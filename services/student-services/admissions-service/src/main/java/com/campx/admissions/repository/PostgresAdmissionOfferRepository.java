package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.AdmissionOfferEntity;
import com.campx.admissions.model.OfferStatus;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.admission_offers} table.
 */
public class PostgresAdmissionOfferRepository implements AdmissionOfferRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAdmissionOfferRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresAdmissionOfferRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresAdmissionOfferRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public AdmissionOfferEntity save(AdmissionOfferEntity offer) {
        if (offer == null) throw new IllegalArgumentException("Offer cannot be null");
        if (offer.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (offer.getId() == null) {
            offer.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.admission_offers (" +
                "tenant_id, id, application_id, decision_id, offer_code, offer_terms_version, " +
                "valid_from, valid_to, status, accepted_at, declined_at, decline_reason, " +
                "extension_granted, extension_granted_by, extended_valid_to, created_at, updated_at, " +
                "created_by, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, id) DO UPDATE SET " +
                "status = EXCLUDED.status, " +
                "accepted_at = EXCLUDED.accepted_at, " +
                "declined_at = EXCLUDED.declined_at, " +
                "decline_reason = EXCLUDED.decline_reason, " +
                "extension_granted = EXCLUDED.extension_granted, " +
                "extension_granted_by = EXCLUDED.extension_granted_by, " +
                "extended_valid_to = EXCLUDED.extended_valid_to, " +
                "updated_at = clock_timestamp(), " +
                "row_version = stu.admission_offers.row_version + 1 " +
                "RETURNING row_version, updated_at";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, offer.getTenantId());
            ps.setObject(idx++, offer.getId());
            ps.setObject(idx++, offer.getApplicationId());
            ps.setObject(idx++, offer.getDecisionId());
            ps.setString(idx++, offer.getOfferCode());
            ps.setString(idx++, offer.getOfferTermsVersion() != null ? offer.getOfferTermsVersion() : "1.0.0");
            ps.setObject(idx++, offer.getValidFrom() != null ? offer.getValidFrom() : OffsetDateTime.now());
            ps.setObject(idx++, offer.getValidTo());
            ps.setString(idx++, offer.getStatus() != null ? offer.getStatus().name() : OfferStatus.ISSUED.name());
            ps.setObject(idx++, offer.getAcceptedAt());
            ps.setObject(idx++, offer.getDeclinedAt());
            ps.setString(idx++, offer.getDeclineReason());
            ps.setBoolean(idx++, offer.isExtensionGranted());
            ps.setObject(idx++, offer.getExtensionGrantedBy());
            ps.setObject(idx++, offer.getExtendedValidTo());
            ps.setObject(idx++, offer.getCreatedAt() != null ? offer.getCreatedAt() : OffsetDateTime.now());
            ps.setObject(idx++, OffsetDateTime.now());
            ps.setObject(idx++, offer.getCreatedBy());
            ps.setInt(idx++, offer.getRowVersion() > 0 ? offer.getRowVersion() : 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    offer.setRowVersion(rs.getInt("row_version"));
                    offer.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                }
            }
            return offer;

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AdmissionsConflictException("Duplicate admission offer code detected: " + offer.getOfferCode());
            }
            throw new RuntimeException("PostgreSQL save failed for admission offer: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AdmissionOfferEntity> findById(UUID tenantId, UUID id) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? AND id = ?";
        List<AdmissionOfferEntity> list = queryList(sql, tenantId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionOfferEntity> findByOfferCode(UUID tenantId, String offerCode) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? AND offer_code = ?";
        List<AdmissionOfferEntity> list = queryList(sql, tenantId, offerCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<AdmissionOfferEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? AND application_id = ? ORDER BY created_at DESC LIMIT 1";
        List<AdmissionOfferEntity> list = queryList(sql, tenantId, applicationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<AdmissionOfferEntity> findExpiredOffers(UUID tenantId, OffsetDateTime cutoff) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? AND status = 'ISSUED' AND valid_to <= ? ORDER BY valid_to ASC";
        return queryList(sql, tenantId, cutoff);
    }

    @Override
    public List<AdmissionOfferEntity> findByStatus(UUID tenantId, OfferStatus status) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId, status != null ? status.name() : null);
    }

    @Override
    public List<AdmissionOfferEntity> findByTenantId(UUID tenantId) {
        String sql = "SELECT * FROM stu.admission_offers WHERE tenant_id = ? ORDER BY created_at DESC";
        return queryList(sql, tenantId);
    }

    private List<AdmissionOfferEntity> queryList(String sql, Object... params) {
        List<AdmissionOfferEntity> results = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL query failed: " + e.getMessage(), e);
        }
        return results;
    }

    private AdmissionOfferEntity mapRow(ResultSet rs) throws SQLException {
        AdmissionOfferEntity o = new AdmissionOfferEntity();
        o.setTenantId(rs.getObject("tenant_id", UUID.class));
        o.setId(rs.getObject("id", UUID.class));
        o.setApplicationId(rs.getObject("application_id", UUID.class));
        o.setDecisionId(rs.getObject("decision_id", UUID.class));
        o.setOfferCode(rs.getString("offer_code"));
        o.setOfferTermsVersion(rs.getString("offer_terms_version"));
        o.setValidFrom(rs.getObject("valid_from", OffsetDateTime.class));
        o.setValidTo(rs.getObject("valid_to", OffsetDateTime.class));

        String st = rs.getString("status");
        if (st != null) o.setStatus(OfferStatus.valueOf(st));

        o.setAcceptedAt(rs.getObject("accepted_at", OffsetDateTime.class));
        o.setDeclinedAt(rs.getObject("declined_at", OffsetDateTime.class));
        o.setDeclineReason(rs.getString("decline_reason"));
        o.setExtensionGranted(rs.getBoolean("extension_granted"));
        o.setExtensionGrantedBy(rs.getObject("extension_granted_by", UUID.class));
        o.setExtendedValidTo(rs.getObject("extended_valid_to", OffsetDateTime.class));
        o.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        o.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        o.setCreatedBy(rs.getObject("created_by", UUID.class));
        o.setRowVersion(rs.getInt("row_version"));
        return o;
    }
}

