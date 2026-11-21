import fs from "fs";
import path from "path";

function write(p, c) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, c);
  console.log("W", p);
}

const L01 =
  "services/administration-services/iadm-01-tenant-lifecycle-service/src/main/java/com/campx/iadm/lifecycle";
const T01 =
  "services/administration-services/iadm-01-tenant-lifecycle-service/src/test/java/com/campx/iadm/lifecycle";
const L02 =
  "services/administration-services/iadm-02-platform-config-service/src/main/java/com/campx/iadm/config";
const T02 =
  "services/administration-services/iadm-02-platform-config-service/src/test/java/com/campx/iadm/config";

write(
  `${L01}/config/DatabaseConfig.java`,
  `package com.campx.iadm.lifecycle.config;

public class DatabaseConfig {
    private String jdbcUrl = firstEnv("SUPABASE_JDBC_URL", "CAMPX_DB_URL", "");
    private String username = firstEnv("SUPABASE_USER", "CAMPX_DB_USERNAME", "postgres");
    private String password = firstEnv("SUPABASE_PASSWORD", "CAMPX_DB_PASSWORD", "");
    private String driverClassName = "org.postgresql.Driver";
    private int maximumPoolSize = 8;
    private int minimumIdle = 1;
    private long connectionTimeoutMs = 30000L;
    private long idleTimeoutMs = 600000L;
    private long maxLifetimeMs = 1800000L;
    private String poolName = "CampXSync-IADM01-Pool";

    private static String firstEnv(String a, String b, String d) {
        String v = System.getenv(a);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(b);
        return (v != null && !v.isEmpty()) ? v : d;
    }

    public static DatabaseConfig load() { return new DatabaseConfig(); }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String v) { this.jdbcUrl = v; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getDriverClassName() { return driverClassName; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public int getMinimumIdle() { return minimumIdle; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public String getPoolName() { return poolName; }
    public void setPoolName(String v) { this.poolName = v; }
}
`
);

write(
  `${L01}/config/DatabaseConnectionManager.java`,
  `package com.campx.iadm.lifecycle.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnectionManager implements AutoCloseable {
    private static volatile DatabaseConnectionManager instance;
    private final DatabaseConfig config;
    private HikariDataSource dataSource;
    private boolean initialized;

    public DatabaseConnectionManager() { this(DatabaseConfig.load()); }
    public DatabaseConnectionManager(DatabaseConfig config) { this.config = config; }

    public static DatabaseConnectionManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnectionManager.class) {
                if (instance == null) instance = new DatabaseConnectionManager();
            }
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) { instance.close(); instance = null; }
    }

    public synchronized void initialize() {
        if (initialized && dataSource != null && !dataSource.isClosed()) return;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getJdbcUrl());
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        hc.setDriverClassName(config.getDriverClassName());
        hc.setMaximumPoolSize(config.getMaximumPoolSize());
        hc.setMinimumIdle(config.getMinimumIdle());
        hc.setConnectionTimeout(config.getConnectionTimeoutMs());
        hc.setIdleTimeout(config.getIdleTimeoutMs());
        hc.setMaxLifetime(config.getMaxLifetimeMs());
        hc.setPoolName(config.getPoolName());
        hc.addDataSourceProperty("prepareThreshold", "0");
        dataSource = new HikariDataSource(hc);
        initialized = true;
    }

    public Connection getConnection() throws SQLException {
        if (!initialized) initialize();
        return dataSource.getConnection();
    }

    @Override public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        initialized = false;
    }
}
`
);

write(
  `${L01}/exception/IadmException.java`,
  `package com.campx.iadm.lifecycle.exception;

public class IadmException extends RuntimeException {
    private final int status;
    private final String errorCode;
    public IadmException(int status, String errorCode, String message) {
        super(message); this.status = status; this.errorCode = errorCode;
    }
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
`
);

write(
  `${L01}/security/UserSecurityContext.java`,
  `package com.campx.iadm.lifecycle.security;

import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.exception.IadmException;
import java.sql.*;
import java.util.UUID;
import java.util.function.Function;

/** Tenant-scoped security context; DB session uses non-privileged role so RLS applies. */
public class UserSecurityContext {
    private final UUID userId;
    private final UUID tenantId;
    private final boolean platformScope;

    public UserSecurityContext(UUID userId, UUID tenantId, boolean platformScope) {
        if (userId == null) throw new IadmException(401, "IADM01_AUTH", "Missing user id");
        if (tenantId == null && !platformScope) throw new IadmException(401, "IADM01_AUTH", "Missing tenant id");
        this.userId = userId; this.tenantId = tenantId; this.platformScope = platformScope;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public boolean isPlatformScope() { return platformScope; }

    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader, boolean allowPlatform) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty())
            throw new IadmException(401, "IADM01_AUTH", "Missing X-User-Id");
        UUID uid = UUID.fromString(userIdHeader.trim());
        UUID tid = null;
        if (tenantIdHeader != null && !tenantIdHeader.trim().isEmpty()) {
            tid = UUID.fromString(tenantIdHeader.trim());
        } else if (!allowPlatform) {
            throw new IadmException(401, "IADM01_AUTH", "Missing X-Tenant-Id");
        }
        return new UserSecurityContext(uid, tid, allowPlatform && tid == null);
    }

    public void applyClaimsAndRole(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("SET LOCAL ROLE authenticated");
        }
        String claims = tenantId != null
            ? String.format("{\\"sub\\":\\"%s\\",\\"role\\":\\"authenticated\\",\\"tenant_id\\":\\"%s\\",\\"app_metadata\\":{\\"tenant_id\\":\\"%s\\"}}", userId, tenantId, tenantId)
            : String.format("{\\"sub\\":\\"%s\\",\\"role\\":\\"authenticated\\"}", userId);
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claims);
            ps.execute();
        }
    }

    public void requirePermission(Connection conn, String permissionCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, permissionCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean(1)) return;
            }
        }
        try (PreparedStatement ps2 = conn.prepareStatement("SELECT iam.is_platform_admin()")) {
            try (ResultSet rs2 = ps2.executeQuery()) {
                if (rs2.next() && rs2.getBoolean(1)) return;
            }
        }
        throw new IadmException(403, "IADM01_FORBIDDEN",
            "Missing permission key " + permissionCode + " (permission-key check, not role name)");
    }

    public <T> T executeInTransaction(Function<Connection, T> work) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                applyClaimsAndRole(conn);
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw ex;
            } catch (Exception ex) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw new IadmException(500, "IADM01_TX", ex.getMessage());
            } finally {
                try { conn.setAutoCommit(prev); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new IadmException(500, "IADM01_DB", e.getMessage());
        }
    }
}
`
);

write(
  `${L01}/security/GatewayHmacVerifier.java`,
  `package com.campx.iadm.lifecycle.security;

import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;
import com.sun.net.httpserver.HttpExchange;

public class GatewayHmacVerifier {
    private final boolean authEnabled;
    private final String internalSecret;
    private final long replayWindowSeconds;

    public GatewayHmacVerifier() {
        this.authEnabled = GatewayHmacSecurityConfig.isInternalAuthEnabled();
        this.internalSecret = this.authEnabled ? GatewayHmacSecurityConfig.getInternalSecret() : null;
        this.replayWindowSeconds = GatewayHmacSecurityConfig.getReplayWindowSeconds();
    }

    public static class VerificationResult {
        public final boolean success;
        public final int status;
        public final String errorCode;
        public final String message;
        public final String userId;
        public final String tenantId;
        public final String userRole;
        private VerificationResult(boolean success, int status, String errorCode, String message,
                                   String userId, String tenantId, String userRole) {
            this.success = success; this.status = status; this.errorCode = errorCode; this.message = message;
            this.userId = userId; this.tenantId = tenantId; this.userRole = userRole;
        }
        public static VerificationResult success(String userId, String tenantId, String userRole) {
            return new VerificationResult(true, 200, null, null, userId, tenantId, userRole);
        }
        public static VerificationResult failure(int status, String errorCode, String message) {
            return new VerificationResult(false, status, errorCode, message, null, null, null);
        }
    }

    public VerificationResult verify(HttpExchange exchange, byte[] body) {
        if (!authEnabled) {
            String userId = header(exchange, "X-User-Id");
            String tenantId = header(exchange, "X-Tenant-Id");
            String role = header(exchange, "X-User-Role");
            return VerificationResult.success(userId, tenantId, role);
        }
        try {
            GatewayHmacProtocol.VerifiedIdentity id = GatewayHmacProtocol.verify(
                exchange, body, internalSecret, replayWindowSeconds);
            return VerificationResult.success(id.getUserId(), id.getTenantId(), id.getUserRole());
        } catch (Exception e) {
            return VerificationResult.failure(401, "IADM01_HMAC", e.getMessage());
        }
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }
}
`
);

console.log("phase1 done");
