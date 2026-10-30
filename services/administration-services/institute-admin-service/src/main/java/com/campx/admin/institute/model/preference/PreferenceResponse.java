package com.campx.admin.institute.model.preference;

/**
 * Standardized HTTP API response model for preference retrieval and persistence endpoints.
 */
public class PreferenceResponse {

    private final boolean success;
    private final UserPreferences preferences;
    private final boolean isAccountSynced;
    private final String message;

    public PreferenceResponse(boolean success, UserPreferences preferences, boolean isAccountSynced, String message) {
        this.success = success;
        this.preferences = preferences;
        this.isAccountSynced = isAccountSynced;
        this.message = message != null ? message : "";
    }

    public boolean isSuccess() {
        return success;
    }

    public UserPreferences getPreferences() {
        return preferences;
    }

    public boolean isAccountSynced() {
        return isAccountSynced;
    }

    public String getMessage() {
        return message;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":").append(success).append(",");
        sb.append("\"isAccountSynced\":").append(isAccountSynced).append(",");
        if (!message.isEmpty()) {
            sb.append("\"message\":\"").append(escape(message)).append("\",");
        }
        sb.append("\"preferences\":").append(preferences != null ? preferences.toJson() : "{}");
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
