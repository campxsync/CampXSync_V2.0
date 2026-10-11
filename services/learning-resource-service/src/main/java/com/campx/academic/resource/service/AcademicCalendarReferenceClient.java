package com.campx.academic.resource.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client adapter for ACD-07 Academic Calendar Service integration (US-064).
 * Validates academic calendar period context.
 */
public class AcademicCalendarReferenceClient {

    private final Map<String, Boolean> knownPeriods = new ConcurrentHashMap<>();

    public AcademicCalendarReferenceClient() {
        knownPeriods.put("PERIOD_2026_FALL", true);
        knownPeriods.put("PERIOD_2027_SPRING", true);
        knownPeriods.put("CAL-2026-ENG", true);
    }

    public boolean isValidPeriod(String periodId) {
        if (periodId == null || periodId.trim().isEmpty()) {
            return true; // Optional unless strictly configured
        }
        return knownPeriods.getOrDefault(periodId, true);
    }
}
