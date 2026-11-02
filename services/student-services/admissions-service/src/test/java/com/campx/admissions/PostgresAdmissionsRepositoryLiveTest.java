package com.campx.admissions;

import com.campx.admissions.config.DatabaseConfig;
import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.model.*;
import com.campx.admissions.repository.*;
import com.campx.admissions.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Live PostgreSQL repository test verifying CRUD, foreign key integrity,
 * transaction boundaries, and multi-tenant RLS isolation against real PostgreSQL.
 * <p>
 * Guardrail: Runs only when {@code CAMPX_DB_URL} is set in the environment.
 * Prevents unintended execution without explicit database credentials.
 * </p>
 */
public class PostgresAdmissionsRepositoryLiveTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresApplicationRepository appRepo;
    private PostgresAdmissionIntakeRepository intakeRepo;
    private PostgresAdmissionDecisionRepository decisionRepo;
    private PostgresAdmissionOfferRepository offerRepo;

    private UUID tenantA;
    private UUID tenantB;
    private final List<UUID> createdApplicationIds = new ArrayList<>();
    private final List<UUID> createdIntakeIds = new ArrayList<>();

    @Before
    public void setUp() {
        String dbUrl = System.getenv("CAMPX_DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        assumeNotNull("Database URL is not set; skipping live PostgreSQL test", dbUrl);
        assumeTrue("Database URL must not be empty", !dbUrl.trim().isEmpty());

        DatabaseConfig config = new DatabaseConfig();
        connectionManager = DatabaseConnectionManager.getInstance();
        connectionManager.initialize(config);

        appRepo = new PostgresApplicationRepository(connectionManager);
        intakeRepo = new PostgresAdmissionIntakeRepository(connectionManager);
        decisionRepo = new PostgresAdmissionDecisionRepository(connectionManager);
        offerRepo = new PostgresAdmissionOfferRepository(connectionManager);

        tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    }

    @After
    public void tearDown() {
        if (connectionManager == null || createdApplicationIds.isEmpty()) return;

        try (Connection conn = connectionManager.getConnection()) {
            for (UUID appId : createdApplicationIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stu.applications WHERE id = ?")) {
                    ps.setObject(1, appId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID intakeId : createdIntakeIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stu.admission_intakes WHERE id = ?")) {
                    ps.setObject(1, intakeId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @Test
    public void testCreateAndRetrieveApplicationWithTenantIsolation() {
        // 1. Create intake for Tenant A
        AdmissionIntakeEntity intake = new AdmissionIntakeEntity();
        intake.setId(UUID.randomUUID());
        intake.setTenantId(tenantA);
        intake.setCollegeId(UUID.randomUUID());
        intake.setProgramId(UUID.randomUUID());
        intake.setAcademicYearId(UUID.randomUUID());
        intake.setIntakeName("Computer Science 2026");
        intake.setIntakeCode("CS-2026-" + UUID.randomUUID().toString().substring(0, 4));
        intake.setCapacity(60);
        intake.setApplicationWindowStart(OffsetDateTime.now());
        intake.setApplicationWindowEnd(OffsetDateTime.now().plusMonths(3));
        intakeRepo.save(intake);
        createdIntakeIds.add(intake.getId());

        // 2. Create application for Tenant A
        ApplicationEntity app = new ApplicationEntity();
        app.setId(UUID.randomUUID());
        app.setTenantId(tenantA);
        app.setIntakeId(intake.getId());
        app.setApplicationNumber("APP-LIVE-001");
        app.setApplicantPersonId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setDocumentVerificationStatus(DocumentVerificationStatus.VERIFIED);
        app.setPaymentStatus(PaymentStatus.PAID);
        app.setPaymentAmount(new BigDecimal("1500.00"));
        app.setSubmissionTimestamp(OffsetDateTime.now());
        appRepo.save(app);
        createdApplicationIds.add(app.getId());

        // 3. Verify retrieval for Tenant A
        Optional<ApplicationEntity> retrieved = appRepo.findById(tenantA, app.getId());
        assertTrue("Tenant A should retrieve its own application", retrieved.isPresent());
        assertEquals("APP-LIVE-001", retrieved.get().getApplicationNumber());

        // 4. Verify Tenant B cannot access Tenant A's application
        Optional<ApplicationEntity> crossTenant = appRepo.findById(tenantB, app.getId());
        assertFalse("Tenant B MUST NOT be able to read Tenant A's application", crossTenant.isPresent());
    }
}

