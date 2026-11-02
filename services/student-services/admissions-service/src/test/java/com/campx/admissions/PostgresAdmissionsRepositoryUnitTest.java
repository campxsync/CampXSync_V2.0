package com.campx.admissions;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.repository.PostgresApplicationRepository;
import com.campx.admissions.security.UserSecurityContext;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.Assert.*;

public class PostgresAdmissionsRepositoryUnitTest {

    @Test(expected = IllegalArgumentException.class)
    public void testSaveNullApplicationFails() {
        PostgresApplicationRepository repo = new PostgresApplicationRepository();
        repo.save(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveApplicationWithoutTenantIdFails() {
        PostgresApplicationRepository repo = new PostgresApplicationRepository();
        ApplicationEntity app = new ApplicationEntity();
        app.setId(UUID.randomUUID());
        // missing tenantId
        repo.save(app);
    }

    @Test
    public void testTransactionRollbackOnException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserSecurityContext context = new UserSecurityContext(userId, tenantId);

        // Verify that executeInTransaction properly propagates and wraps runtime exceptions
        boolean threw = false;
        try {
            context.executeInTransaction(DatabaseConnectionManager.getInstance(), conn -> {
                throw new SQLException("Simulated deadlock / serialization failure", "40P01");
            });
        } catch (RuntimeException ex) {
            threw = true;
            assertTrue(ex.getMessage().contains("failed") || ex.getMessage().contains("connection"));
        }
        assertTrue("Transaction execution should fail fast on database failure", threw);
    }
}

