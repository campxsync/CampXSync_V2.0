package com.campx.student.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portal-owned preferences for STM-10 (widgets, shortcuts, language/display).
 * Persisted in {@code stu.student_portal_preferences} (STM-10-EN-001).
 */
public class PortalPreferencesDto {

    private UUID studentId;
    private UUID collegeId;
    private int version = 1;
    private DashboardWidgetsConfig widgets = new DashboardWidgetsConfig();
    private List<ShortcutItem> shortcuts = new ArrayList<>();
    private DisplayPreferences display = new DisplayPreferences();

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public DashboardWidgetsConfig getWidgets() { return widgets; }
    public void setWidgets(DashboardWidgetsConfig widgets) {
        this.widgets = widgets != null ? widgets : new DashboardWidgetsConfig();
    }

    public List<ShortcutItem> getShortcuts() { return shortcuts; }
    public void setShortcuts(List<ShortcutItem> shortcuts) {
        this.shortcuts = shortcuts != null ? shortcuts : new ArrayList<>();
    }

    public DisplayPreferences getDisplay() { return display; }
    public void setDisplay(DisplayPreferences display) {
        this.display = display != null ? display : new DisplayPreferences();
    }

    public static class DashboardWidgetsConfig {
        private List<String> order = new ArrayList<>();
        private List<String> hidden = new ArrayList<>();

        public List<String> getOrder() { return order; }
        public void setOrder(List<String> order) {
            this.order = order != null ? order : new ArrayList<>();
        }

        public List<String> getHidden() { return hidden; }
        public void setHidden(List<String> hidden) {
            this.hidden = hidden != null ? hidden : new ArrayList<>();
        }
    }

    public static class ShortcutItem {
        private String id;
        private String label;
        private String path;

        public ShortcutItem() {}

        public ShortcutItem(String id, String label, String path) {
            this.id = id;
            this.label = label;
            this.path = path;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    public static class DisplayPreferences {
        private String theme = "light";
        private String locale = "en-IN";
        private String timezone = "Asia/Kolkata";
        private String tableDensity = "comfortable";
        private Map<String, Boolean> accessibility = new HashMap<>();

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }

        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public String getTableDensity() { return tableDensity; }
        public void setTableDensity(String tableDensity) { this.tableDensity = tableDensity; }

        public Map<String, Boolean> getAccessibility() { return accessibility; }
        public void setAccessibility(Map<String, Boolean> accessibility) {
            this.accessibility = accessibility != null ? accessibility : new HashMap<>();
        }
    }
}
