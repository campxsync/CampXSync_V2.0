package com.campx.admin.institute.model.preference;

/**
 * Accessibility constraints and visual ergonomics.
 */
public class AccessibilityConfig {

    private boolean highContrast = false;
    private boolean reducedMotion = false;
    private boolean largeFont = false;

    public AccessibilityConfig() {
    }

    public AccessibilityConfig(boolean highContrast, boolean reducedMotion, boolean largeFont) {
        this.highContrast = highContrast;
        this.reducedMotion = reducedMotion;
        this.largeFont = largeFont;
    }

    public boolean isHighContrast() {
        return highContrast;
    }

    public void setHighContrast(boolean highContrast) {
        this.highContrast = highContrast;
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    public boolean isLargeFont() {
        return largeFont;
    }

    public void setLargeFont(boolean largeFont) {
        this.largeFont = largeFont;
    }

    public String toJson() {
        return "{"
                + "\"highContrast\":" + highContrast + ","
                + "\"reducedMotion\":" + reducedMotion + ","
                + "\"largeFont\":" + largeFont
                + "}";
    }
}
