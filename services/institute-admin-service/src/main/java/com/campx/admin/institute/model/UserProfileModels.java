package com.campx.admin.institute.model;

import java.util.*;

/**
 * Domain and DTO models for ADM-01 User Profile Management.
 * Explicitly implements an allow-list for JSON serialization to guarantee zero leakage
 * of tokens, passwords, secrets, or internal metadata.
 */
public class UserProfileModels {

    /**
     * Allowed user profile status values in iam.user_profiles.
     */
    public static final Set<String> ALLOWED_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("ACTIVE", "SUSPENDED", "LOCKED", "INACTIVE"))
    );

    /**
     * Allowed user profile status values on creation (POST).
     * LOCKED and SUSPENDED are strictly disallowed during initial creation.
     */
    public static final Set<String> ALLOWED_CREATE_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("ACTIVE", "INACTIVE"))
    );

    /**
     * Whitelisted fields for query sorting.
     */
    public static final Map<String, String> SORT_FIELD_WHITELIST;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("full_name", "full_name");
        map.put("fullname", "full_name");
        map.put("name", "full_name");
        map.put("email", "email");
        map.put("username", "username");
        map.put("status", "status");
        map.put("created_at", "created_at");
        map.put("createdat", "created_at");
        map.put("updated_at", "updated_at");
        map.put("updatedat", "updated_at");
        SORT_FIELD_WHITELIST = Collections.unmodifiableMap(map);
    }

    /**
     * User Profile Entity and Response DTO (Allow-list representation).
     */
    public static class UserProfile {
        private String id;
        private String tenantId;
        private String personId;
        private String collegeId;
        private String departmentId;
        private String username;
        private String email;
        private String fullName;
        private String status;
        private String preferences;
        private String lastSeenAt;
        private String createdAt;
        private String updatedAt;
        private String createdBy;
        private String updatedBy;
        private int rowVersion;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getPersonId() { return personId; }
        public void setPersonId(String personId) { this.personId = personId; }

        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getPreferences() { return preferences; }
        public void setPreferences(String preferences) { this.preferences = preferences; }

        public String getLastSeenAt() { return lastSeenAt; }
        public void setLastSeenAt(String lastSeenAt) { this.lastSeenAt = lastSeenAt; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public int getRowVersion() { return rowVersion; }
        public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(id).append("\",");
            sb.append("\"tenantId\":\"").append(tenantId).append("\",");
            if (personId != null) sb.append("\"personId\":\"").append(personId).append("\",");
            else sb.append("\"personId\":null,");
            if (collegeId != null) sb.append("\"collegeId\":\"").append(collegeId).append("\",");
            else sb.append("\"collegeId\":null,");
            if (departmentId != null) sb.append("\"departmentId\":\"").append(departmentId).append("\",");
            else sb.append("\"departmentId\":null,");
            if (username != null) sb.append("\"username\":\"").append(escape(username)).append("\",");
            else sb.append("\"username\":null,");
            sb.append("\"email\":\"").append(escape(email)).append("\",");
            sb.append("\"fullName\":\"").append(escape(fullName)).append("\",");
            sb.append("\"status\":\"").append(status).append("\",");
            if (preferences != null && !preferences.trim().isEmpty()) {
                sb.append("\"preferences\":").append(preferences.trim()).append(",");
            } else {
                sb.append("\"preferences\":{},");
            }
            if (lastSeenAt != null) sb.append("\"lastSeenAt\":\"").append(lastSeenAt).append("\",");
            else sb.append("\"lastSeenAt\":null,");
            sb.append("\"createdAt\":\"").append(createdAt).append("\",");
            sb.append("\"updatedAt\":\"").append(updatedAt).append("\",");
            if (createdBy != null) sb.append("\"createdBy\":\"").append(createdBy).append("\",");
            else sb.append("\"createdBy\":null,");
            if (updatedBy != null) sb.append("\"updatedBy\":\"").append(updatedBy).append("\",");
            else sb.append("\"updatedBy\":null,");
            sb.append("\"rowVersion\":").append(rowVersion);
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * DTO for POST /api/v1/admin/users
     */
    public static class CreateUserProfileRequest {
        private String id; // Required Supabase Auth user ID
        private String email;
        private String fullName;
        private String username;
        private String status = "ACTIVE";
        private String collegeId;
        private String departmentId;
        private String personId;
        private String preferences;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getPersonId() { return personId; }
        public void setPersonId(String personId) { this.personId = personId; }

        public String getPreferences() { return preferences; }
        public void setPreferences(String preferences) { this.preferences = preferences; }
    }

    /**
     * DTO for PUT /api/v1/admin/users/{id}
     */
    public static class UpdateUserProfileRequest {
        private String fullName;
        private String username;
        private String email;
        private String collegeId;
        private String departmentId;
        private String personId;
        private String preferences;
        private Integer rowVersion;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getPersonId() { return personId; }
        public void setPersonId(String personId) { this.personId = personId; }

        public String getPreferences() { return preferences; }
        public void setPreferences(String preferences) { this.preferences = preferences; }

        public Integer getRowVersion() { return rowVersion; }
        public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
    }

    /**
     * DTO for PATCH /api/v1/admin/users/{id}/status.
     * <p>
     * Note: The {@code reason} field is an administrative audit annotation logged with actor and correlation
     * context. It is not persisted in the {@code iam.user_profiles} table because the table schema does not
     * include a reason column.
     */
    public static class UpdateUserStatusRequest {
        private String status;
        private Integer rowVersion;
        private String reason;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Integer getRowVersion() { return rowVersion; }
        public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * Query filter criteria for GET /api/v1/admin/users
     */
    public static class UserProfileFilter {
        private String status;
        private String collegeId;
        private String departmentId;
        private String query;
        private String sortField = "full_name";
        private String sortOrder = "ASC";
        private int page = 1;
        private int limit = 20;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCollegeId() { return collegeId; }
        public void setCollegeId(String collegeId) { this.collegeId = collegeId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public String getSortField() { return sortField; }
        public void setSortField(String sortField) { this.sortField = sortField; }

        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
    }

    /**
     * Paginated collection response wrapper for user profiles.
     */
    public static class UserProfilePage {
        private final List<UserProfile> users;
        private final int page;
        private final int limit;
        private final int totalCount;
        private final int totalPages;

        public UserProfilePage(List<UserProfile> users, int page, int limit, int totalCount) {
            this.users = users != null ? users : Collections.emptyList();
            this.page = page;
            this.limit = limit;
            this.totalCount = totalCount;
            this.totalPages = limit > 0 ? (int) Math.ceil((double) totalCount / limit) : 0;
        }

        public List<UserProfile> getUsers() { return users; }
        public int getPage() { return page; }
        public int getLimit() { return limit; }
        public int getTotalCount() { return totalCount; }
        public int getTotalPages() { return totalPages; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"users\":[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(users.get(i).toJson());
            }
            sb.append("],");
            sb.append("\"pagination\":{");
            sb.append("\"page\":").append(page).append(",");
            sb.append("\"limit\":").append(limit).append(",");
            sb.append("\"totalCount\":").append(totalCount).append(",");
            sb.append("\"totalPages\":").append(totalPages).append(",");
            sb.append("\"hasNext\":").append(page < totalPages).append(",");
            sb.append("\"hasPrevious\":").append(page > 1);
            sb.append("}}");
            return sb.toString();
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
