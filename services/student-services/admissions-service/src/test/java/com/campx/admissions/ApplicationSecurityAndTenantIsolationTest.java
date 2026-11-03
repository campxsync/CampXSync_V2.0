package com.campx.admissions;

import com.campx.admissions.exception.AdmissionsSecurityViolationException;
import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.repository.ApplicationRepository;
import com.campx.admissions.repository.InMemoryApplicationRepository;
import com.campx.admissions.security.AdmissionsPermissionEvaluator;
import com.campx.admissions.security.GatewayHmacVerifier;
import com.campx.admissions.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class ApplicationSecurityAndTenantIsolationTest {

    private ApplicationRepository applicationRepository;
    private GatewayHmacVerifier hmacVerifier;
    private static final String SHARED_SECRET = "test-campxsync-hmac-secret-key-32b!";

    private UUID tenantA;
    private UUID tenantB;
    private UUID applicantA;
    private UUID applicantB;

    @Before
    public void setUp() {
        applicationRepository = new InMemoryApplicationRepository();
        hmacVerifier = new GatewayHmacVerifier(SHARED_SECRET);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        applicantA = UUID.randomUUID();
        applicantB = UUID.randomUUID();

        // Seed application for Tenant A
        ApplicationEntity appA = new ApplicationEntity();
        appA.setTenantId(tenantA);
        appA.setId(UUID.randomUUID());
        appA.setApplicantPersonId(applicantA);
        appA.setApplicationNumber("APP-TENANT-A-001");
        appA.setStatus(ApplicationStatus.DRAFT);
        appA.setRowVersion(0);
        applicationRepository.save(appA);
    }

    @Test
    public void testStrictTenantIsolationOnRepositoryQuery() {
        // Querying with Tenant A returns the application
        UUID appId = applicationRepository.findByApplicant(tenantA, applicantA).get(0).getId();
        Optional<ApplicationEntity> foundInA = applicationRepository.findById(tenantA, appId);
        assertTrue(foundInA.isPresent());

        // Cross-tenant query: Querying the exact same application ID under Tenant B returns empty
        Optional<ApplicationEntity> foundInB = applicationRepository.findById(tenantB, appId);
        assertFalse("Cross-tenant access must return empty / not found", foundInB.isPresent());
    }

    @Test(expected = AdmissionsSecurityViolationException.class)
    public void testApplicantCannotAccessAnotherApplicantsApplication() {
        UserSecurityContext contextApplicantB = new UserSecurityContext(applicantB, tenantA, null, null, "APPLICANT", false);

        // Attempting to evaluate ownership for applicantA's record using applicantB's context must fail
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(contextApplicantB, applicantA);
    }

    @Test
    public void testStaffCanAccessApplicantData() {
        UserSecurityContext officerContext = new UserSecurityContext(UUID.randomUUID(), tenantA, null, null, "ADMISSIONS_OFFICER", false);
        // Staff should pass ownership check
        AdmissionsPermissionEvaluator.requireApplicationOwnershipOrStaff(officerContext, applicantA);
    }

    @Test
    public void testValidHmacSignatureAccepted() {
        long timestamp = Instant.now().getEpochSecond();
        String method = "POST";
        String path = "/api/v1/admissions/applications";
        String body = "{\"intakeId\":\"" + UUID.randomUUID() + "\"}";

        String canonical = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + body;
        String signature = GatewayHmacVerifier.calculateHmac(canonical, SHARED_SECRET);

        // Should not throw
        hmacVerifier.verify(method, path, String.valueOf(timestamp), signature, body);
    }

    @Test(expected = AdmissionsSecurityViolationException.class)
    public void testForgedHmacSignatureRejected() {
        long timestamp = Instant.now().getEpochSecond();
        String method = "POST";
        String path = "/api/v1/admissions/applications";
        String body = "{\"intakeId\":\"" + UUID.randomUUID() + "\"}";

        hmacVerifier.verify(method, path, String.valueOf(timestamp), "tampered-forged-signature-string", body);
    }

    @Test(expected = AdmissionsSecurityViolationException.class)
    public void testExpiredHmacTimestampRejected() {
        // Timestamp 600 seconds in the past (> 300s window)
        long expiredTimestamp = Instant.now().getEpochSecond() - 600;
        String method = "GET";
        String path = "/api/v1/admissions/intakes";
        String body = "";

        String canonical = method.toUpperCase() + "\n" + path + "\n" + expiredTimestamp + "\n" + body;
        String signature = GatewayHmacVerifier.calculateHmac(canonical, SHARED_SECRET);

        hmacVerifier.verify(method, path, String.valueOf(expiredTimestamp), signature, body);
    }

    @Test(expected = IllegalStateException.class)
    public void testAdmissionsApplicationFailsFastWithoutDbInDeployedEnvironment() {
        com.campx.admissions.config.DatabaseConfig emptyConfig = new com.campx.admissions.config.DatabaseConfig();
        emptyConfig.setJdbcUrl(null);
        AdmissionsApplication.validatePersistenceConfiguration(emptyConfig, true);
    }

    @Test
    public void testAdmissionsApplicationAllowsValidDbInDeployedEnvironment() {
        com.campx.admissions.config.DatabaseConfig validConfig = new com.campx.admissions.config.DatabaseConfig();
        validConfig.setJdbcUrl("jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres");
        AdmissionsApplication.validatePersistenceConfiguration(validConfig, true);
    }

    @Test
    public void testAdmissionsApplicationAllowsEmptyDbInLocalOrTestEnvironment() {
        com.campx.admissions.config.DatabaseConfig emptyConfig = new com.campx.admissions.config.DatabaseConfig();
        emptyConfig.setJdbcUrl(null);
        AdmissionsApplication.validatePersistenceConfiguration(emptyConfig, false);
    }
}
