package com.campx.student.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for student locale, timezone, communication, and accessibility preferences (STM01-US-022, STM01-US-045).
 */
public class StudentPreferencesDto {

    private String locale = "en-IN";
    private String timezone = "Asia/Kolkata";
    private List<String> communicationChannels = new ArrayList<>();
    private Map<String, Object> accessibilitySettings = new HashMap<>();

    public StudentPreferencesDto() {}

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public List<String> getCommunicationChannels() { return communicationChannels; }
    public void setCommunicationChannels(List<String> communicationChannels) {
        this.communicationChannels = communicationChannels != null ? communicationChannels : new ArrayList<>();
    }

    public Map<String, Object> getAccessibilitySettings() { return accessibilitySettings; }
    public void setAccessibilitySettings(Map<String, Object> accessibilitySettings) {
        this.accessibilitySettings = accessibilitySettings != null ? accessibilitySettings : new HashMap<>();
    }
}
