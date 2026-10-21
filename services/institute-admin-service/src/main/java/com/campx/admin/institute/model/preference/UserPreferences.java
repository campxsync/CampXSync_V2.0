package com.campx.admin.institute.model.preference;

import com.campx.admin.institute.exception.PreferenceValidationException;

import java.util.*;

/**
 * CampXSync Typed and Versioned User Preference Domain Model (Wave 2, Schema v1).
 * <p>
 * Implements strict Precedence Order:
 * <ol>
 *   <li>Security / Accessibility constraints (highContrast, reducedMotion, largeFont)</li>
 *   <li>Tenant-enforced configuration and regional defaults (locale, timezone)</li>
 *   <li>Authenticated User and Role settings</li>
 *   <li>User and Module settings (role-scoped dashboard widget layout)</li>
 *   <li>Product defaults</li>
 * </ol>
 * </p>
 */
public class UserPreferences {

    public static final int CURRENT_VERSION = 1;

    public static final Set<String> ALLOWED_THEMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "light", "dark", "system"
    )));

    public static final Set<String> ALLOWED_TABLE_DENSITIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "comfortable", "compact"
    )));

    public static final Set<Integer> ALLOWED_DATE_RANGE_DAYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            7, 14, 30
    )));

    public static final Set<String> ALLOWED_ATTENDANCE_FILTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "all", "unmarked", "shortage"
    )));

    public static final Map<String, RoleDashboardConfig> ROLE_DEFAULT_WIDGETS;
    static {
        Map<String, RoleDashboardConfig> map = new LinkedHashMap<>();
        map.put("college_admin", new RoleDashboardConfig(
                Arrays.asList("kpis", "trends", "batches", "shortage", "missed", "recent"),
                Collections.emptyList()
        ));
        map.put("faculty", new RoleDashboardConfig(
                Arrays.asList("today", "upcoming", "missed"),
                Collections.emptyList()
        ));
        map.put("platform_admin", new RoleDashboardConfig(
                Arrays.asList("kpis", "lifecycle", "institutes", "audit"),
                Collections.emptyList()
        ));
        ROLE_DEFAULT_WIDGETS = Collections.unmodifiableMap(map);
    }

    private int version = CURRENT_VERSION;
    private String theme = "light";
    private boolean sidebarCollapsed = false;
    private String tableDensity = "comfortable";
    private int defaultDateRangeDays = 14;
    private String defaultAttendanceFilter = "all";
    private String locale = "en-IN";
    private String timezone = "Asia/Kolkata";
    private AccessibilityConfig accessibility = new AccessibilityConfig();
    private RoleDashboardConfig dashboardWidgets = new RoleDashboardConfig(
            ROLE_DEFAULT_WIDGETS.get("college_admin").getOrder(),
            ROLE_DEFAULT_WIDGETS.get("college_admin").getHidden()
    );
    private Map<String, RoleDashboardConfig> roles = new LinkedHashMap<>();

    public UserPreferences() {
        for (Map.Entry<String, RoleDashboardConfig> entry : ROLE_DEFAULT_WIDGETS.entrySet()) {
            this.roles.put(entry.getKey(), new RoleDashboardConfig(entry.getValue().getOrder(), entry.getValue().getHidden()));
        }
    }

    public static UserPreferences getProductDefaults() {
        return new UserPreferences();
    }

    public static RoleDashboardConfig getRoleDefaultWidgets(String role) {
        if (role != null) {
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            if (ROLE_DEFAULT_WIDGETS.containsKey(normalized)) {
                RoleDashboardConfig def = ROLE_DEFAULT_WIDGETS.get(normalized);
                return new RoleDashboardConfig(def.getOrder(), def.getHidden());
            }
        }
        RoleDashboardConfig def = ROLE_DEFAULT_WIDGETS.get("college_admin");
        return new RoleDashboardConfig(def.getOrder(), def.getHidden());
    }

    // Getters and Setters

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void setSidebarCollapsed(boolean sidebarCollapsed) {
        this.sidebarCollapsed = sidebarCollapsed;
    }

    public String getTableDensity() {
        return tableDensity;
    }

    public void setTableDensity(String tableDensity) {
        this.tableDensity = tableDensity;
    }

    public int getDefaultDateRangeDays() {
        return defaultDateRangeDays;
    }

    public void setDefaultDateRangeDays(int defaultDateRangeDays) {
        this.defaultDateRangeDays = defaultDateRangeDays;
    }

    public String getDefaultAttendanceFilter() {
        return defaultAttendanceFilter;
    }

    public void setDefaultAttendanceFilter(String defaultAttendanceFilter) {
        this.defaultAttendanceFilter = defaultAttendanceFilter;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public AccessibilityConfig getAccessibility() {
        return accessibility;
    }

    public void setAccessibility(AccessibilityConfig accessibility) {
        this.accessibility = accessibility != null ? accessibility : new AccessibilityConfig();
    }

    public RoleDashboardConfig getDashboardWidgets() {
        return dashboardWidgets;
    }

    public void setDashboardWidgets(RoleDashboardConfig dashboardWidgets) {
        this.dashboardWidgets = dashboardWidgets;
    }

    public Map<String, RoleDashboardConfig> getRoles() {
        return roles;
    }

    public void setRoles(Map<String, RoleDashboardConfig> roles) {
        this.roles = roles != null ? new LinkedHashMap<>(roles) : new LinkedHashMap<>();
    }

    /**
     * Validates that all fields strictly comply with schema version 1 constraints.
     * Throws {@link PreferenceValidationException} (HTTP 422) on any violation.
     */
    public void validate() {
        if (version != CURRENT_VERSION) {
            throw new PreferenceValidationException("Unsupported preference schema version: " + version + ". Supported version: " + CURRENT_VERSION);
        }
        if (theme == null || !ALLOWED_THEMES.contains(theme.trim().toLowerCase(Locale.ROOT))) {
            throw new PreferenceValidationException("Invalid theme '" + theme + "'. Allowed values: " + ALLOWED_THEMES);
        }
        if (tableDensity == null || !ALLOWED_TABLE_DENSITIES.contains(tableDensity.trim().toLowerCase(Locale.ROOT))) {
            throw new PreferenceValidationException("Invalid tableDensity '" + tableDensity + "'. Allowed values: " + ALLOWED_TABLE_DENSITIES);
        }
        if (!ALLOWED_DATE_RANGE_DAYS.contains(defaultDateRangeDays)) {
            throw new PreferenceValidationException("Invalid defaultDateRangeDays " + defaultDateRangeDays + ". Allowed values: " + ALLOWED_DATE_RANGE_DAYS);
        }
        if (defaultAttendanceFilter == null || !ALLOWED_ATTENDANCE_FILTERS.contains(defaultAttendanceFilter.trim().toLowerCase(Locale.ROOT))) {
            throw new PreferenceValidationException("Invalid defaultAttendanceFilter '" + defaultAttendanceFilter + "'. Allowed values: " + ALLOWED_ATTENDANCE_FILTERS);
        }
        if (locale != null && locale.length() > 10) {
            throw new PreferenceValidationException("Field 'locale' exceeds maximum length of 10 characters");
        }
        if (timezone != null && timezone.length() > 50) {
            throw new PreferenceValidationException("Field 'timezone' exceeds maximum length of 50 characters");
        }
        if (dashboardWidgets != null) {
            dashboardWidgets.validate();
        }
        if (roles != null) {
            for (Map.Entry<String, RoleDashboardConfig> entry : roles.entrySet()) {
                if (entry.getValue() != null) {
                    entry.getValue().validate();
                }
            }
        }
    }

    /**
     * Deep-merges an incoming preference payload into this existing preference instance.
     * Preserves role-scoped widget configs for roles not updated by the caller,
     * updates the target role configuration, and revalidates the outcome.
     *
     * @param incoming   the new preferences submitted by the caller
     * @param activeRole the authenticated caller's effective role (e.g. 'college_admin', 'faculty')
     * @return a new, fully validated, merged {@link UserPreferences} instance
     */
    public UserPreferences deepMerge(UserPreferences incoming, String activeRole) {
        if (incoming == null) {
            return this;
        }

        String normRole = (activeRole != null && !activeRole.trim().isEmpty())
                ? activeRole.trim().toLowerCase(Locale.ROOT)
                : "college_admin";

        UserPreferences merged = new UserPreferences();
        merged.setVersion(incoming.getVersion() > 0 ? incoming.getVersion() : this.version);
        merged.setTheme(incoming.getTheme() != null ? incoming.getTheme() : this.theme);
        merged.setSidebarCollapsed(incoming.isSidebarCollapsed());
        merged.setTableDensity(incoming.getTableDensity() != null ? incoming.getTableDensity() : this.tableDensity);
        merged.setDefaultDateRangeDays(incoming.getDefaultDateRangeDays() > 0 ? incoming.getDefaultDateRangeDays() : this.defaultDateRangeDays);
        merged.setDefaultAttendanceFilter(incoming.getDefaultAttendanceFilter() != null ? incoming.getDefaultAttendanceFilter() : this.defaultAttendanceFilter);
        merged.setLocale(incoming.getLocale() != null ? incoming.getLocale() : this.locale);
        merged.setTimezone(incoming.getTimezone() != null ? incoming.getTimezone() : this.timezone);
        merged.setAccessibility(incoming.getAccessibility() != null ? incoming.getAccessibility() : this.accessibility);

        // Deep merge role-scoped configs
        Map<String, RoleDashboardConfig> mergedRoles = new LinkedHashMap<>();
        if (this.roles != null) {
            for (Map.Entry<String, RoleDashboardConfig> e : this.roles.entrySet()) {
                mergedRoles.put(e.getKey(), new RoleDashboardConfig(e.getValue().getOrder(), e.getValue().getHidden()));
            }
        }

        // Apply incoming role configurations if present
        if (incoming.getRoles() != null && !incoming.getRoles().isEmpty()) {
            for (Map.Entry<String, RoleDashboardConfig> e : incoming.getRoles().entrySet()) {
                if (e.getValue() != null) {
                    mergedRoles.put(e.getKey(), new RoleDashboardConfig(e.getValue().getOrder(), e.getValue().getHidden()));
                }
            }
        }

        // Apply incoming dashboardWidgets to the target activeRole
        RoleDashboardConfig activeWidgets = incoming.getDashboardWidgets();
        if (activeWidgets == null) {
            activeWidgets = mergedRoles.get(normRole);
            if (activeWidgets == null) {
                activeWidgets = getRoleDefaultWidgets(normRole);
            }
        }
        mergedRoles.put(normRole, activeWidgets);
        merged.setRoles(mergedRoles);
        merged.setDashboardWidgets(activeWidgets);

        merged.validate();
        return merged;
    }

    /**
     * Serializes this preference instance to canonical RFC 8259 JSON format.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"version\":").append(version).append(",");
        sb.append("\"theme\":\"").append(escape(theme)).append("\",");
        sb.append("\"sidebarCollapsed\":").append(sidebarCollapsed).append(",");
        sb.append("\"tableDensity\":\"").append(escape(tableDensity)).append("\",");
        sb.append("\"defaultDateRangeDays\":").append(defaultDateRangeDays).append(",");
        sb.append("\"defaultAttendanceFilter\":\"").append(escape(defaultAttendanceFilter)).append("\",");
        sb.append("\"locale\":\"").append(escape(locale != null ? locale : "en-IN")).append("\",");
        sb.append("\"timezone\":\"").append(escape(timezone != null ? timezone : "Asia/Kolkata")).append("\",");
        sb.append("\"accessibility\":").append(accessibility != null ? accessibility.toJson() : "{}").append(",");
        sb.append("\"dashboardWidgets\":").append(dashboardWidgets != null ? dashboardWidgets.toJson() : "{}").append(",");
        sb.append("\"roles\":{");
        if (roles != null) {
            int i = 0;
            for (Map.Entry<String, RoleDashboardConfig> entry : roles.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(entry.getKey())).append("\":");
                sb.append(entry.getValue() != null ? entry.getValue().toJson() : "{}");
                i++;
            }
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses a JSON string representation into a validated {@link UserPreferences} instance.
     * Accepts either a direct preference JSON or a wrapped format {@code {"preferences": {...}, "role": "..."}}.
     */
    public static UserPreferences fromJson(String json, String requestedRole) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            UserPreferences prefs = getProductDefaults();
            prefs.resolveActiveDashboardWidgets(requestedRole);
            return prefs;
        }

        Map<String, Object> map = PreferenceJsonParser.parseObject(json);
        return fromMap(map, requestedRole);
    }

    @SuppressWarnings("unchecked")
    public static UserPreferences fromMap(Map<String, Object> map, String requestedRole) {
        if (map == null) {
            return getProductDefaults();
        }

        // Support wrapped format: {"preferences": {...}, "role": "..."}
        if (map.containsKey("preferences") && map.get("preferences") instanceof Map) {
            Object roleObj = map.get("role");
            String roleFromPayload = roleObj instanceof String ? (String) roleObj : requestedRole;
            return fromMap((Map<String, Object>) map.get("preferences"), roleFromPayload);
        }

        UserPreferences p = new UserPreferences();

        if (map.containsKey("version") && map.get("version") instanceof Number) {
            p.setVersion(((Number) map.get("version")).intValue());
        }

        if (map.containsKey("theme") && map.get("theme") instanceof String) {
            p.setTheme(((String) map.get("theme")).trim().toLowerCase(Locale.ROOT));
        }

        if (map.containsKey("sidebarCollapsed") && map.get("sidebarCollapsed") instanceof Boolean) {
            p.setSidebarCollapsed((Boolean) map.get("sidebarCollapsed"));
        }

        if (map.containsKey("tableDensity") && map.get("tableDensity") instanceof String) {
            p.setTableDensity(((String) map.get("tableDensity")).trim().toLowerCase(Locale.ROOT));
        }

        if (map.containsKey("defaultDateRangeDays") && map.get("defaultDateRangeDays") instanceof Number) {
            p.setDefaultDateRangeDays(((Number) map.get("defaultDateRangeDays")).intValue());
        }

        if (map.containsKey("defaultAttendanceFilter") && map.get("defaultAttendanceFilter") instanceof String) {
            p.setDefaultAttendanceFilter(((String) map.get("defaultAttendanceFilter")).trim().toLowerCase(Locale.ROOT));
        }

        if (map.containsKey("locale") && map.get("locale") instanceof String) {
            p.setLocale((String) map.get("locale"));
        }

        if (map.containsKey("timezone") && map.get("timezone") instanceof String) {
            p.setTimezone((String) map.get("timezone"));
        }

        if (map.containsKey("accessibility") && map.get("accessibility") instanceof Map) {
            Map<String, Object> accMap = (Map<String, Object>) map.get("accessibility");
            AccessibilityConfig acc = new AccessibilityConfig();
            if (accMap.containsKey("highContrast") && accMap.get("highContrast") instanceof Boolean) {
                acc.setHighContrast((Boolean) accMap.get("highContrast"));
            }
            if (accMap.containsKey("reducedMotion") && accMap.get("reducedMotion") instanceof Boolean) {
                acc.setReducedMotion((Boolean) accMap.get("reducedMotion"));
            }
            if (accMap.containsKey("largeFont") && accMap.get("largeFont") instanceof Boolean) {
                acc.setLargeFont((Boolean) accMap.get("largeFont"));
            }
            p.setAccessibility(acc);
        }

        // Roles map
        Map<String, RoleDashboardConfig> rolesMap = new LinkedHashMap<>();
        if (map.containsKey("roles") && map.get("roles") instanceof Map) {
            Map<String, Object> rMap = (Map<String, Object>) map.get("roles");
            for (Map.Entry<String, Object> e : rMap.entrySet()) {
                if (e.getValue() instanceof Map) {
                    rolesMap.put(e.getKey(), parseRoleDashboardConfig((Map<String, Object>) e.getValue()));
                }
            }
        }
        if (!rolesMap.isEmpty()) {
            p.setRoles(rolesMap);
        }

        // Active dashboard widgets
        if (map.containsKey("dashboardWidgets") && map.get("dashboardWidgets") instanceof Map) {
            p.setDashboardWidgets(parseRoleDashboardConfig((Map<String, Object>) map.get("dashboardWidgets")));
        } else {
            p.resolveActiveDashboardWidgets(requestedRole);
        }

        return p;
    }

    public void resolveActiveDashboardWidgets(String role) {
        String normRole = (role != null && !role.trim().isEmpty())
                ? role.trim().toLowerCase(Locale.ROOT)
                : "college_admin";
        if (roles != null && roles.containsKey(normRole)) {
            this.dashboardWidgets = roles.get(normRole);
        } else {
            this.dashboardWidgets = getRoleDefaultWidgets(normRole);
            if (this.roles != null) {
                this.roles.put(normRole, this.dashboardWidgets);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static RoleDashboardConfig parseRoleDashboardConfig(Map<String, Object> map) {
        RoleDashboardConfig cfg = new RoleDashboardConfig();
        if (map.containsKey("order") && map.get("order") instanceof List) {
            List<Object> rawList = (List<Object>) map.get("order");
            List<String> order = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof String) {
                    order.add((String) item);
                }
            }
            cfg.setOrder(order);
        }
        if (map.containsKey("hidden") && map.get("hidden") instanceof List) {
            List<Object> rawList = (List<Object>) map.get("hidden");
            List<String> hidden = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof String) {
                    hidden.add((String) item);
                }
            }
            cfg.setHidden(hidden);
        }
        return cfg;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
