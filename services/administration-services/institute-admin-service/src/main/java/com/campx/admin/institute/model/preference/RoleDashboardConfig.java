package com.campx.admin.institute.model.preference;

import com.campx.admin.institute.exception.PreferenceValidationException;

import java.util.*;

/**
 * Role-scoped dashboard widget layout configuration.
 */
public class RoleDashboardConfig {

    public static final Set<String> ALLOWED_WIDGETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "kpis",
            "trends",
            "batches",
            "shortage",
            "missed",
            "recent",
            "today",
            "upcoming",
            "lifecycle",
            "institutes",
            "audit"
    )));

    private List<String> order = new ArrayList<>();
    private List<String> hidden = new ArrayList<>();

    public RoleDashboardConfig() {
    }

    public RoleDashboardConfig(List<String> order, List<String> hidden) {
        if (order != null) this.order = new ArrayList<>(order);
        if (hidden != null) this.hidden = new ArrayList<>(hidden);
    }

    public List<String> getOrder() {
        return order;
    }

    public void setOrder(List<String> order) {
        this.order = order != null ? new ArrayList<>(order) : new ArrayList<>();
    }

    public List<String> getHidden() {
        return hidden;
    }

    public void setHidden(List<String> hidden) {
        this.hidden = hidden != null ? new ArrayList<>(hidden) : new ArrayList<>();
    }

    public void validate() {
        if (order != null) {
            if (order.size() > 20) {
                throw new PreferenceValidationException("Dashboard widget order list exceeds maximum limit of 20 items");
            }
            for (String widget : order) {
                if (widget == null || !ALLOWED_WIDGETS.contains(widget.trim())) {
                    throw new PreferenceValidationException("Disallowed dashboard widget identifier in order: " + widget);
                }
            }
        }
        if (hidden != null) {
            if (hidden.size() > 20) {
                throw new PreferenceValidationException("Dashboard widget hidden list exceeds maximum limit of 20 items");
            }
            for (String widget : hidden) {
                if (widget == null || !ALLOWED_WIDGETS.contains(widget.trim())) {
                    throw new PreferenceValidationException("Disallowed dashboard widget identifier in hidden: " + widget);
                }
            }
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"order\":[");
        if (order != null) {
            for (int i = 0; i < order.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(order.get(i))).append("\"");
            }
        }
        sb.append("],");
        sb.append("\"hidden\":[");
        if (hidden != null) {
            for (int i = 0; i < hidden.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(hidden.get(i))).append("\"");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
