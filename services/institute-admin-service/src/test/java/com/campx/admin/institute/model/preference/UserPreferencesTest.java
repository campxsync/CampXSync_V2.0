package com.campx.admin.institute.model.preference;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.PreferenceValidationException;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests validating schema constraints, RFC 8259 serialization/deserialization,
 * and deep-merge behavior for {@link UserPreferences}.
 */
public class UserPreferencesTest {

    @Test
    public void testProductDefaultsValid() {
        UserPreferences defaults = UserPreferences.getProductDefaults();
        assertNotNull(defaults);
        assertEquals(1, defaults.getVersion());
        assertEquals("light", defaults.getTheme());
        assertFalse(defaults.isSidebarCollapsed());
        assertEquals("comfortable", defaults.getTableDensity());
        assertEquals(14, defaults.getDefaultDateRangeDays());
        assertEquals("all", defaults.getDefaultAttendanceFilter());
        assertEquals("en-IN", defaults.getLocale());
        assertEquals("Asia/Kolkata", defaults.getTimezone());

        assertNotNull(defaults.getAccessibility());
        assertFalse(defaults.getAccessibility().isHighContrast());
        assertFalse(defaults.getAccessibility().isReducedMotion());
        assertFalse(defaults.getAccessibility().isLargeFont());

        assertNotNull(defaults.getDashboardWidgets());
        assertNotNull(defaults.getRoles());
        assertTrue(defaults.getRoles().containsKey("college_admin"));
        assertTrue(defaults.getRoles().containsKey("faculty"));
        assertTrue(defaults.getRoles().containsKey("platform_admin"));

        // Must validate cleanly without exception
        defaults.validate();
    }

    @Test
    public void testJsonRoundTrip() {
        UserPreferences original = UserPreferences.getProductDefaults();
        original.setTheme("dark");
        original.setSidebarCollapsed(true);
        original.setTableDensity("compact");
        original.setDefaultDateRangeDays(30);
        original.setDefaultAttendanceFilter("shortage");
        original.getAccessibility().setHighContrast(true);
        original.getDashboardWidgets().setOrder(Arrays.asList("batches", "trends", "kpis"));

        String json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"theme\":\"dark\""));
        assertTrue(json.contains("\"sidebarCollapsed\":true"));
        assertTrue(json.contains("\"tableDensity\":\"compact\""));
        assertTrue(json.contains("\"defaultDateRangeDays\":30"));
        assertTrue(json.contains("\"highContrast\":true"));

        UserPreferences parsed = UserPreferences.fromJson(json, "college_admin");
        assertNotNull(parsed);
        assertEquals(1, parsed.getVersion());
        assertEquals("dark", parsed.getTheme());
        assertTrue(parsed.isSidebarCollapsed());
        assertEquals("compact", parsed.getTableDensity());
        assertEquals(30, parsed.getDefaultDateRangeDays());
        assertEquals("shortage", parsed.getDefaultAttendanceFilter());
        assertTrue(parsed.getAccessibility().isHighContrast());
        assertEquals(Arrays.asList("batches", "trends", "kpis"), parsed.getDashboardWidgets().getOrder());
        parsed.validate();
    }

    @Test
    public void testWrappedPayloadParsing() {
        String wrappedJson = "{"
                + "\"role\":\"faculty\","
                + "\"preferences\":{"
                + "  \"version\":1,"
                + "  \"theme\":\"system\","
                + "  \"sidebarCollapsed\":false,"
                + "  \"tableDensity\":\"comfortable\","
                + "  \"defaultDateRangeDays\":7,"
                + "  \"defaultAttendanceFilter\":\"unmarked\","
                + "  \"dashboardWidgets\":{\"order\":[\"today\",\"upcoming\"],\"hidden\":[\"missed\"]}"
                + "}"
                + "}";

        UserPreferences parsed = UserPreferences.fromJson(wrappedJson, "faculty");
        assertNotNull(parsed);
        assertEquals("system", parsed.getTheme());
        assertEquals(7, parsed.getDefaultDateRangeDays());
        assertEquals("unmarked", parsed.getDefaultAttendanceFilter());
        assertEquals(Arrays.asList("today", "upcoming"), parsed.getDashboardWidgets().getOrder());
        assertEquals(Collections.singletonList("missed"), parsed.getDashboardWidgets().getHidden());
        parsed.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsUnsupportedVersion() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.setVersion(2);
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsInvalidTheme() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.setTheme("neon_cyberpunk");
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsInvalidTableDensity() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.setTableDensity("spacious");
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsInvalidDateRangeDays() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.setDefaultDateRangeDays(60);
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsInvalidAttendanceFilter() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.setDefaultAttendanceFilter("irregular");
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsDisallowedWidget() {
        UserPreferences p = UserPreferences.getProductDefaults();
        p.getDashboardWidgets().setOrder(Arrays.asList("kpis", "bitcoin_ticker"));
        p.validate();
    }

    @Test(expected = PreferenceValidationException.class)
    public void testRejectsTooManyWidgets() {
        UserPreferences p = UserPreferences.getProductDefaults();
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tooMany.add("kpis");
        }
        p.getDashboardWidgets().setOrder(tooMany);
        p.validate();
    }

    @Test
    public void testDeepMergePreservesOtherRoleConfigs() {
        UserPreferences base = UserPreferences.getProductDefaults();
        // Base has college_admin, faculty, platform_admin
        List<String> initialCollegeAdminOrder = base.getRoles().get("college_admin").getOrder();
        List<String> initialPlatformAdminOrder = base.getRoles().get("platform_admin").getOrder();

        // Faculty user updates their theme to dark and customizes faculty widgets
        UserPreferences incoming = new UserPreferences();
        incoming.setTheme("dark");
        incoming.setTableDensity("compact");
        RoleDashboardConfig facultyWidgets = new RoleDashboardConfig(
                Arrays.asList("upcoming", "today"),
                Collections.singletonList("missed")
        );
        incoming.setDashboardWidgets(facultyWidgets);

        UserPreferences merged = base.deepMerge(incoming, "faculty");

        // Merged preferences should reflect theme & density
        assertEquals("dark", merged.getTheme());
        assertEquals("compact", merged.getTableDensity());

        // Active role widgets must match the incoming faculty customization
        assertEquals(Arrays.asList("upcoming", "today"), merged.getDashboardWidgets().getOrder());
        assertEquals(Collections.singletonList("missed"), merged.getDashboardWidgets().getHidden());

        // Crucial test: college_admin and platform_admin configurations MUST be strictly preserved!
        assertNotNull(merged.getRoles().get("college_admin"));
        assertEquals(initialCollegeAdminOrder, merged.getRoles().get("college_admin").getOrder());

        assertNotNull(merged.getRoles().get("platform_admin"));
        assertEquals(initialPlatformAdminOrder, merged.getRoles().get("platform_admin").getOrder());

        // Faculty role in the roles map must match the new customization
        assertEquals(Arrays.asList("upcoming", "today"), merged.getRoles().get("faculty").getOrder());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testJsonParserRejectsMalformedJson() {
        PreferenceJsonParser.parseObject("{ \"theme\": \"dark\"");
    }
}
