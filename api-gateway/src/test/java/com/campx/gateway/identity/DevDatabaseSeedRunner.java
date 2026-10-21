package com.campx.gateway.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Controlled, target-verified seed runner for Wave 0 test data.
 * <p>
 * Enforces strict project identity guards before executing any mutation:
 * <ul>
 *   <li>Target pooler username MUST be postgres.azulbaxszsqxymqmlwfe</li>
 *   <li>Rejects prohibited project refs (ekspahbsbwdpraaqfqui, kpwdvvcschankgggitnj)</li>
 *   <li>Executes wave0_dev_test_seed.sql in an explicit transaction</li>
 * </ul>
 * </p>
 */
public class DevDatabaseSeedRunner {

    public static final String APPROVED_PROJECT_REF = "azulbaxszsqxymqmlwfe";
    public static final String PROHIBITED_PROD_REF = "ekspahbsbwdpraaqfqui";
    public static final String PROHIBITED_STAGING_REF = "kpwdvvcschankgggitnj";

    @Test
    public void executeSeedTransaction() throws Exception {
        String envUrl = System.getenv("SUPABASE_JDBC_URL");
        String user = System.getenv("SUPABASE_USER");
        String pass = System.getenv("SUPABASE_PASSWORD");

        if (envUrl == null || user == null || pass == null) {
            fail("FATAL: Missing database credentials in environment");
        }

        // Target verification guardrails
        if (!user.contains(APPROVED_PROJECT_REF)) {
            fail("FATAL TARGET REJECTION: Username does not match approved dev project ref: " + APPROVED_PROJECT_REF);
        }

        if (user.contains(PROHIBITED_PROD_REF) || envUrl.contains(PROHIBITED_PROD_REF)) {
            fail("FATAL SAFETY VIOLATION: Connection configuration targets PROD project ref: " + PROHIBITED_PROD_REF);
        }

        if (user.contains(PROHIBITED_STAGING_REF) || envUrl.contains(PROHIBITED_STAGING_REF)) {
            fail("FATAL SAFETY VIOLATION: Connection configuration targets prohibited staging ref: " + PROHIBITED_STAGING_REF);
        }

        String targetJdbcUrl = envUrl.contains("ap-southeast-1")
                ? envUrl.replace("ap-southeast-1", "ap-south-1")
                : envUrl;

        System.out.println(">>> Target Verification Passed: Approved Project Ref = " + APPROVED_PROJECT_REF);
        System.out.println(">>> Target User = " + user);
        System.out.println(">>> Target Host = aws-0-ap-south-1.pooler.supabase.com:6543");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(targetJdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(15000);

        try (HikariDataSource ds = new HikariDataSource(config);
             Connection conn = ds.getConnection()) {

            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Verify server
                try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                    if (rs.next()) {
                        System.out.println(">>> Connected to DB: " + rs.getString(1) + ", user: " + rs.getString(2) + ", server: " + rs.getString(3));
                    }
                }

                String seedPath = "d:/CampXSync/CampXSync_V2.0/database/seed-data/wave0_dev_test_seed.sql";
                String seedSql = new String(Files.readAllBytes(Paths.get(seedPath)), java.nio.charset.StandardCharsets.UTF_8);

                System.out.println(">>> Executing wave0_dev_test_seed.sql in transaction...");
                stmt.execute(seedSql);

                conn.commit();
                System.out.println(">>> SUCCESS: Transaction COMMITTED successfully.");
            } catch (Exception e) {
                conn.rollback();
                System.err.println(">>> ROLLED BACK transaction due to error: " + e.getMessage());
                throw e;
            }
        }
    }
}
