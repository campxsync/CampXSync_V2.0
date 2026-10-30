package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.UserProfileModels.*;
import static com.campx.admin.institute.model.UserProfileModels.*;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PostgreSQL JDBC repository for iam.user_profiles table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class UserProfileRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserProfileRepository.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final DatabaseConnectionManager connectionManager;

    public UserProfileRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public UserProfileRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Escapes SQL ILIKE wildcard characters (\, %, _) to ensure exact substring matching.
     *
     * @param query raw search string
     * @return escaped search pattern
     */
    public static String escapeIlikePattern(String query) {
        if (query == null) return "";
        return query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Retrieves a single non-deleted user profile by ID within the caller's tenant and scope.
     */
    public UserProfile getUserById(UserSecurityContext context, UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            context.checkPermission(conn, "adm08.view.all");

            String sql = "SELECT id, tenant_id, person_id, college_id, department_id, username, email, full_name, "
                    + "status, preferences, last_seen_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at "
                    + "FROM iam.user_profiles "
                    + "WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new UserProfileNotFoundException(id.toString(), context.getTenantId().toString());
        });
    }

    /**
     * Lists non-deleted user profiles matching filter, search query, sorting, and pagination.
     */
    public UserProfilePage listUsers(UserSecurityContext context, UserProfileFilter filter) {
        if (filter == null) {
            filter = new UserProfileFilter();
        }

        UserProfileFilter effectiveFilter = filter;

        return context.executeInTransaction(connectionManager, conn -> {
            context.checkPermission(conn, "adm08.view.all");

            StringBuilder whereClause = new StringBuilder(" WHERE deleted_at IS NULL");
            List<Object> params = new ArrayList<>();

            if (effectiveFilter.getStatus() != null && !effectiveFilter.getStatus().trim().isEmpty()) {
                whereClause.append(" AND status = ?");
                params.add(effectiveFilter.getStatus().trim().toUpperCase(Locale.ROOT));
            }

            if (effectiveFilter.getCollegeId() != null && !effectiveFilter.getCollegeId().trim().isEmpty()) {
                whereClause.append(" AND college_id = ?");
                params.add(UUID.fromString(effectiveFilter.getCollegeId().trim()));
            }

            if (effectiveFilter.getDepartmentId() != null && !effectiveFilter.getDepartmentId().trim().isEmpty()) {
                whereClause.append(" AND department_id = ?");
                params.add(UUID.fromString(effectiveFilter.getDepartmentId().trim()));
            }

            if (effectiveFilter.getQuery() != null && !effectiveFilter.getQuery().trim().isEmpty()) {
                String term = "%" + escapeIlikePattern(effectiveFilter.getQuery().trim()) + "%";
                whereClause.append(" AND (full_name ILIKE ? ESCAPE '\\' OR email ILIKE ? ESCAPE '\\' OR username ILIKE ? ESCAPE '\\')");
                params.add(term);
                params.add(term);
                params.add(term);
            }

            // 1. Total count query
            String countSql = "SELECT count(*) FROM iam.user_profiles" + whereClause;
            int totalCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                setParameters(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getInt(1);
                    }
                }
            }

            // 2. Sorting validation against whitelist
            String rawSortField = effectiveFilter.getSortField();
            if (rawSortField == null || rawSortField.trim().isEmpty()) {
                rawSortField = "full_name";
            }
            String normalizedSortField = rawSortField.trim().toLowerCase(Locale.ROOT);
            String column = SORT_FIELD_WHITELIST.get(normalizedSortField);
            if (column == null) {
                throw new SecurityViolationException("ADM01_INVALID_SORT_FIELD",
                        "Invalid sort field '" + rawSortField + "'. Allowed sort fields: " + SORT_FIELD_WHITELIST.keySet());
            }

            String sortOrder = "ASC";
            if (effectiveFilter.getSortOrder() != null && "DESC".equalsIgnoreCase(effectiveFilter.getSortOrder().trim())) {
                sortOrder = "DESC";
            }

            // 3. Pagination limits
            int page = effectiveFilter.getPage() > 0 ? effectiveFilter.getPage() : 1;
            int limit = effectiveFilter.getLimit() > 0 ? Math.min(effectiveFilter.getLimit(), 100) : 20;
            int offset = (page - 1) * limit;

            String selectSql = "SELECT id, tenant_id, person_id, college_id, department_id, username, email, full_name, "
                    + "status, preferences, last_seen_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at "
                    + "FROM iam.user_profiles"
                    + whereClause
                    + " ORDER BY " + column + " " + sortOrder + ", id ASC"
                    + " LIMIT ? OFFSET ?";

            List<Object> selectParams = new ArrayList<>(params);
            selectParams.add(limit);
            selectParams.add(offset);

            List<UserProfile> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                setParameters(ps, selectParams);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }

            return new UserProfilePage(items, page, limit, totalCount);
        });
    }

    /**
     * Creates a new user profile for an existing Supabase Auth user.
     * Does NOT generate auth user or set tenant_id/created_by/row_version manually.
     */
    public UserProfile createUser(UserSecurityContext context, CreateUserProfileRequest request) {
        if (request == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }

        // 1. Validation
        if (request.getId() == null || request.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Missing required field 'id' (Supabase Auth user ID)");
        }
        UUID authUserId;
        try {
            authUserId = UUID.fromString(request.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new SecurityViolationException("Invalid user ID format (must be UUID): " + request.getId());
        }

        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new MalformedPayloadException("Missing or blank required field 'fullName'");
        }
        String fullName = request.getFullName().trim();
        if (fullName.length() > 255) {
            throw new SecurityViolationException("Field 'fullName' exceeds maximum allowed length of 255 characters");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new MalformedPayloadException("Missing or blank required field 'email'");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 255) {
            throw new SecurityViolationException("Invalid email format or length: " + email);
        }

        String username = null;
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            username = request.getUsername().trim();
            if (username.length() > 100) {
                throw new SecurityViolationException("Field 'username' exceeds maximum allowed length of 100 characters");
            }
        }

        String status = "ACTIVE";
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            status = request.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_CREATE_STATUSES.contains(status)) {
                throw new SecurityViolationException("Invalid status for user creation: '" + status
                        + "'. Allowed statuses on create: " + ALLOWED_CREATE_STATUSES);
            }
        }

        UUID collegeId = parseUuid(request.getCollegeId(), "collegeId");
        UUID departmentId = parseUuid(request.getDepartmentId(), "departmentId");
        UUID personId = parseUuid(request.getPersonId(), "personId");
        String preferences = validatePreferences(request.getPreferences());

        String finalStatus = status;
        String finalUsername = username;

        return context.executeInTransaction(connectionManager, conn -> {
            context.checkPermission(conn, "adm08.write");

            String sql = "INSERT INTO iam.user_profiles ("
                    + "id, person_id, college_id, department_id, username, email, full_name, status, preferences"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) "
                    + "RETURNING id, tenant_id, person_id, college_id, department_id, username, email, full_name, "
                    + "status, preferences, last_seen_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, authUserId);
                ps.setObject(2, personId);
                ps.setObject(3, collegeId);
                ps.setObject(4, departmentId);
                ps.setString(5, finalUsername);
                ps.setString(6, email);
                ps.setString(7, fullName);
                ps.setString(8, finalStatus);
                ps.setString(9, preferences);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, email, finalUsername, authUserId.toString());
            }
            throw new RuntimeException("Failed to insert user profile");
        });
    }

    /**
     * Updates an existing user profile with optimistic locking.
     */
    public UserProfile updateUser(UserSecurityContext context, UUID id, UpdateUserProfileRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (request.getRowVersion() == null) {
            throw new MalformedPayloadException("Missing required optimistic lock field 'rowVersion'");
        }

        String fullName = null;
        if (request.getFullName() != null) {
            fullName = request.getFullName().trim();
            if (fullName.isEmpty()) {
                throw new MalformedPayloadException("Field 'fullName' cannot be blank");
            }
            if (fullName.length() > 255) {
                throw new SecurityViolationException("Field 'fullName' exceeds maximum allowed length of 255 characters");
            }
        }

        String email = null;
        if (request.getEmail() != null) {
            email = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 255) {
                throw new SecurityViolationException("Invalid email format or length: " + email);
            }
        }

        String username = null;
        if (request.getUsername() != null) {
            username = request.getUsername().trim();
            if (username.length() > 100) {
                throw new SecurityViolationException("Field 'username' exceeds maximum allowed length of 100 characters");
            }
        }

        boolean hasCollege = request.getCollegeId() != null;
        UUID collegeId = hasCollege ? parseUuid(request.getCollegeId(), "collegeId") : null;

        boolean hasDept = request.getDepartmentId() != null;
        UUID departmentId = hasDept ? parseUuid(request.getDepartmentId(), "departmentId") : null;

        boolean hasPerson = request.getPersonId() != null;
        UUID personId = hasPerson ? parseUuid(request.getPersonId(), "personId") : null;

        boolean hasPrefs = request.getPreferences() != null;
        String preferences = hasPrefs ? validatePreferences(request.getPreferences()) : null;

        String finalFullName = fullName;
        String finalEmail = email;
        String finalUsername = username;

        return context.executeInTransaction(connectionManager, conn -> {
            context.checkPermission(conn, "adm08.write");

            String sql = "UPDATE iam.user_profiles SET "
                    + "full_name = coalesce(?, full_name), "
                    + "username = coalesce(?, username), "
                    + "email = coalesce(?, email), "
                    + "college_id = CASE WHEN ? THEN ?::uuid ELSE college_id END, "
                    + "department_id = CASE WHEN ? THEN ?::uuid ELSE department_id END, "
                    + "person_id = CASE WHEN ? THEN ?::uuid ELSE person_id END, "
                    + "preferences = CASE WHEN ? THEN ?::jsonb ELSE preferences END "
                    + "WHERE id = ? AND row_version = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, person_id, college_id, department_id, username, email, full_name, "
                    + "status, preferences, last_seen_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, finalFullName);
                ps.setString(2, finalUsername);
                ps.setString(3, finalEmail);
                ps.setBoolean(4, hasCollege);
                ps.setObject(5, collegeId);
                ps.setBoolean(6, hasDept);
                ps.setObject(7, departmentId);
                ps.setBoolean(8, hasPerson);
                ps.setObject(9, personId);
                ps.setBoolean(10, hasPrefs);
                ps.setString(11, preferences);
                ps.setObject(12, id);
                ps.setInt(13, request.getRowVersion());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, finalEmail, finalUsername, id.toString());
            }

            // Disambiguate 0 rows updated
            disambiguateZeroRowsUpdated(conn, id, request.getRowVersion());
            throw new RuntimeException("Unexpected state in updateUser");
        });
    }

    /**
     * Transitions user profile status with state machine validation, self-deactivation guard,
     * and last active tenant administrator guard.
     */
    public UserProfile updateUserStatus(UserSecurityContext context, UUID id, UpdateUserStatusRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new MalformedPayloadException("Request body cannot be null");
        }
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new MalformedPayloadException("Missing required field 'status'");
        }
        if (request.getRowVersion() == null) {
            throw new MalformedPayloadException("Missing required optimistic lock field 'rowVersion'");
        }

        String targetStatus = request.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(targetStatus)) {
            throw new SecurityViolationException("Invalid status: '" + targetStatus + "'. Allowed: " + ALLOWED_STATUSES);
        }

        // Self-suspend / deactivate guard
        if (id.equals(context.getUserId())) {
            if ("SUSPENDED".equals(targetStatus) || "LOCKED".equals(targetStatus) || "INACTIVE".equals(targetStatus)) {
                throw new SecurityViolationException("Callers cannot suspend, lock, or deactivate their own account");
            }
        }

        return context.executeInTransaction(connectionManager, conn -> {
            context.checkPermission(conn, "adm08.write");

            // 1. Fetch current status and row_version
            String currentStatus;
            int currentRowVersion;
            String checkSql = "SELECT status, row_version FROM iam.user_profiles WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentStatus = rs.getString("status");
                        currentRowVersion = rs.getInt("row_version");
                    } else {
                        throw new UserProfileNotFoundException(id.toString(), context.getTenantId().toString());
                    }
                }
            }

            // Check row_version
            if (currentRowVersion != request.getRowVersion()) {
                throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                        String.format("Stale row_version. Expected: %d, Current: %d", request.getRowVersion(), currentRowVersion));
            }

            // 2. Validate status transitions
            validateStatusTransition(currentStatus, targetStatus);

            // 3. Last active tenant admin guard
            if ("SUSPENDED".equals(targetStatus) || "INACTIVE".equals(targetStatus)) {
                enforceLastActiveTenantAdminGuard(conn, id);
            }

            // 4. Execute update
            String updateSql = "UPDATE iam.user_profiles SET status = ? "
                    + "WHERE id = ? AND row_version = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, person_id, college_id, department_id, username, email, full_name, "
                    + "status, preferences, last_seen_at, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, targetStatus);
                ps.setObject(2, id);
                ps.setInt(3, request.getRowVersion());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                mapSqlException(e, null, null, id.toString());
            }

            disambiguateZeroRowsUpdated(conn, id, request.getRowVersion());
            throw new RuntimeException("Unexpected state in updateUserStatus");
        });
    }

    /**
     * Validates life-cycle transitions.
     * Allowed:
     * - ACTIVE <-> SUSPENDED
     * - ACTIVE <-> INACTIVE
     * - SUSPENDED -> ACTIVE, INACTIVE
     * - INACTIVE -> ACTIVE
     * LOCKED is system-managed; transitions to or from LOCKED are disallowed.
     */
    void validateStatusTransition(String current, String target) {
        if (current.equals(target)) {
            return; // No-op allowed
        }
        if ("LOCKED".equals(current) || "LOCKED".equals(target)) {
            throw new InvalidStatusTransitionException(
                    "Status 'LOCKED' is system-managed and cannot be altered or cleared via status transition endpoint");
        }

        if ("ACTIVE".equals(current)) {
            if ("SUSPENDED".equals(target) || "INACTIVE".equals(target)) return;
        } else if ("SUSPENDED".equals(current)) {
            if ("ACTIVE".equals(target) || "INACTIVE".equals(target)) return;
        } else if ("INACTIVE".equals(current)) {
            if ("ACTIVE".equals(target)) return;
        }

        throw new InvalidStatusTransitionException(current, target);
    }

    /**
     * Isolated last-admin guard:
     * Before allowing a tenant-wide TENANT_ADMIN user to be deactivated or suspended,
     * determines whether another currently-valid, non-deleted TENANT_ADMIN assignment
     * exists for the same tenant.
     *
     * @param conn         active database transaction connection
     * @param targetUserId ID of the user undergoing status transition
     * @throws InvalidTenantStateException if the user is a tenant-wide TENANT_ADMIN and no other valid assignment exists
     * @throws SQLException                if query execution fails
     */
    protected void enforceLastActiveTenantAdminGuard(Connection conn, UUID targetUserId) throws SQLException {
        // 1. Check if the target user is currently an active, valid tenant-wide TENANT_ADMIN
        String checkTargetAdminSql = "SELECT 1 FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.tenant_id = core.current_tenant_id() "
                + "AND ra.user_id = ? "
                + "AND ra.deleted_at IS NULL "
                + "AND ra.college_id IS NULL "
                + "AND ra.department_id IS NULL "
                + "AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_DATE) "
                + "AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_DATE) "
                + "AND r.code = 'TENANT_ADMIN' "
                + "AND r.status = 'ACTIVE' "
                + "AND r.deleted_at IS NULL "
                + "LIMIT 1";

        boolean isTargetTenantAdmin = false;
        try (PreparedStatement ps = conn.prepareStatement(checkTargetAdminSql)) {
            ps.setObject(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    isTargetTenantAdmin = true;
                }
            }
        }

        // If target user is not a tenant-wide TENANT_ADMIN, guard does not apply
        if (!isTargetTenantAdmin) {
            return;
        }

        // 2. Check if ANOTHER currently valid, non-deleted tenant-wide TENANT_ADMIN assignment exists
        String checkOtherAdminSql = "SELECT 1 FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.tenant_id = core.current_tenant_id() "
                + "AND ra.user_id != ? "
                + "AND ra.deleted_at IS NULL "
                + "AND ra.college_id IS NULL "
                + "AND ra.department_id IS NULL "
                + "AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_DATE) "
                + "AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_DATE) "
                + "AND r.code = 'TENANT_ADMIN' "
                + "AND r.status = 'ACTIVE' "
                + "AND r.deleted_at IS NULL "
                + "LIMIT 1";

        boolean hasOtherActiveAdmin = false;
        try (PreparedStatement ps = conn.prepareStatement(checkOtherAdminSql)) {
            ps.setObject(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    hasOtherActiveAdmin = true;
                }
            }
        }

        if (!hasOtherActiveAdmin) {
            throw new InvalidTenantStateException("Cannot deactivate or suspend the last active tenant-wide administrator for this tenant");
        }
    }

    /**
     * Disambiguates why an UPDATE statement affected 0 rows:
     * - User does not exist, is soft-deleted, or is invisible under RLS -> 404
     * - User exists and is visible, but row_version does not match -> 409
     * - User exists with matching row_version, but caller lacks row update scope -> 403
     */
    private void disambiguateZeroRowsUpdated(Connection conn, UUID id, int expectedVersion) throws SQLException {
        String query = "SELECT row_version, deleted_at FROM iam.user_profiles WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp deletedAt = rs.getTimestamp("deleted_at");
                    if (deletedAt != null) {
                        throw new UserProfileNotFoundException("User profile is deleted: " + id);
                    }
                    int actualVersion = rs.getInt("row_version");
                    if (actualVersion != expectedVersion) {
                        throw new UserProfileConflictException("ADM01_STALE_ROW_VERSION",
                                String.format("Stale row_version. Expected: %d, Current: %d", expectedVersion, actualVersion));
                    }
                    throw new UserProfileAccessDeniedException("Caller lacks permission to update user profile in this scope");
                }
            }
        }
        throw new UserProfileNotFoundException("User profile not found: " + id);
    }

    /**
     * Maps PostgreSQL SQLExceptions to domain error codes and specific exceptions.
     * Sanitizes output to ensure database schema, raw messages, and internal SQL are never leaked to clients.
     */
    private void mapSqlException(SQLException e, String email, String username, String id) {
        String sqlState = e.getSQLState();
        String message = e.getMessage() != null ? e.getMessage() : "";

        // Log full exception details internally for operational diagnostics
        logger.error("[DatabaseError] SQLState: {}, Error: {}", sqlState, message, e);

        if ("23505".equals(sqlState)) { // Unique constraint violation -> 409 Conflict
            if (message.contains("user_profiles_pkey")) {
                throw new UserProfileConflictException("ADM01_PROFILE_ALREADY_EXISTS",
                        "User profile already exists for user ID: " + id);
            }
            if (message.contains("uq_user_profiles_tenant_id_email")) {
                throw new UserProfileConflictException("ADM01_DUPLICATE_EMAIL",
                        "User profile with this email address already exists in tenant: " + (email != null ? email : ""));
            }
            if (message.contains("uq_user_profiles_tenant_id_username")) {
                throw new UserProfileConflictException("ADM01_DUPLICATE_USERNAME",
                        "User profile with this username already exists in tenant: " + (username != null ? username : ""));
            }
            if (message.contains("uq_user_profiles_tenant_id_person_id")) {
                throw new UserProfileConflictException("ADM01_PERSON_ALREADY_LINKED",
                        "Person record is already linked to another user profile in tenant");
            }
            throw new UserProfileConflictException("ADM01_UNIQUE_VIOLATION", "A unique resource conflict occurred in the tenant");
        }

        if ("23503".equals(sqlState)) { // Foreign key constraint violation -> 400/422 Bad Request
            if (message.contains("fk_user_profiles_college_id")) {
                throw new InvalidUserReferenceException("collegeId", "Referenced college does not exist in current tenant");
            }
            if (message.contains("fk_user_profiles_department_id")) {
                throw new InvalidUserReferenceException("departmentId", "Referenced department does not exist in current tenant");
            }
            if (message.contains("fk_user_profiles_person_id")) {
                throw new InvalidUserReferenceException("personId", "Referenced person does not exist in current tenant");
            }
            if (message.contains("fk_user_profiles_tenant")) {
                throw new InvalidUserReferenceException("tenantId", "Referenced tenant does not exist");
            }
            throw new InvalidUserReferenceException("A referenced entity does not exist or is invalid");
        }

        if ("23514".equals(sqlState)) { // Check constraint violation -> 400 Bad Request
            throw new SecurityViolationException("The provided values violate database validation constraints");
        }

        if ("42501".equals(sqlState)) { // Insufficient privilege -> 403 Forbidden
            throw new UserProfileAccessDeniedException("Access denied: insufficient database permissions to complete this operation");
        }
    }

    private UUID parseUuid(String val, String fieldName) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(val.trim());
        } catch (IllegalArgumentException e) {
            throw new SecurityViolationException(String.format("Invalid UUID format for '%s': %s", fieldName, val));
        }
    }

    private String validatePreferences(String prefs) {
        if (prefs == null || prefs.trim().isEmpty()) {
            return "{}";
        }
        String trimmed = prefs.trim();
        if (trimmed.length() > 32768) {
            throw new SecurityViolationException("Preferences payload exceeds maximum allowed size of 32KB");
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new MalformedPayloadException("Preferences must be a valid JSON object string");
        }
        return trimmed;
    }

    private void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private UserProfile mapRow(ResultSet rs) throws SQLException {
        UserProfile u = new UserProfile();
        u.setId(rs.getString("id"));
        u.setTenantId(rs.getString("tenant_id"));
        u.setPersonId(rs.getString("person_id"));
        u.setCollegeId(rs.getString("college_id"));
        u.setDepartmentId(rs.getString("department_id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        u.setStatus(rs.getString("status"));
        u.setPreferences(rs.getString("preferences"));

        Timestamp lastSeen = rs.getTimestamp("last_seen_at");
        u.setLastSeenAt(lastSeen != null ? lastSeen.toInstant().toString() : null);

        Timestamp created = rs.getTimestamp("created_at");
        u.setCreatedAt(created != null ? created.toInstant().toString() : null);

        Timestamp updated = rs.getTimestamp("updated_at");
        u.setUpdatedAt(updated != null ? updated.toInstant().toString() : null);

        u.setCreatedBy(rs.getString("created_by"));
        u.setUpdatedBy(rs.getString("updated_by"));
        u.setRowVersion(rs.getInt("row_version"));
        return u;
    }
}
