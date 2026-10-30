package com.campx.academic.calendar.service;

import com.campx.academic.calendar.exception.CalendarTermOverlapException;
import com.campx.academic.calendar.model.CalendarModels.*;

import java.util.*;

/**
 * Deterministic validation and conflict detection engine for academic calendars (US-008, US-019, US-020, US-021, US-022).
 */
public class CalendarValidationEngine {

    /**
     * Validates proposed or active terms for date overlaps within the same calendar.
     */
    public void validateTermOverlap(CalendarTerm proposedTerm, List<CalendarTerm> existingTerms) {
        if (proposedTerm == null || proposedTerm.getStartDate() == null || proposedTerm.getEndDate() == null) {
            return;
        }
        for (CalendarTerm other : existingTerms) {
            if (other.getId() != null && other.getId().equals(proposedTerm.getId())) {
                continue; // ignore self
            }
            if (other.getStatus() != TermStatus.ACTIVE) {
                continue;
            }
            if (datesOverlap(proposedTerm.getStartDate(), proposedTerm.getEndDate(), other.getStartDate(), other.getEndDate())) {
                throw new CalendarTermOverlapException("Proposed term '" + proposedTerm.getTermCode() +
                        "' [" + proposedTerm.getStartDate() + " to " + proposedTerm.getEndDate() +
                        "] overlaps with existing term '" + other.getTermCode() +
                        "' [" + other.getStartDate() + " to " + other.getEndDate() + "]");
            }
        }
    }

    /**
     * Runs full deterministic pre-submission / pre-publication validation on a calendar.
     */
    public ValidationReport validateCalendar(AcademicCalendar calendar, List<CalendarTerm> terms, List<CalendarEvent> events) {
        ValidationReport report = new ValidationReport();

        if (calendar == null) {
            report.addIssue("BLOCKING", "CAL_NULL", "Academic calendar cannot be null", "Calendar");
            return report;
        }

        // Rule 1: Effective dates must be valid
        if (calendar.getEffectiveFrom() != null && calendar.getEffectiveTo() != null) {
            if (calendar.getEffectiveFrom().compareTo(calendar.getEffectiveTo()) > 0) {
                report.addIssue("BLOCKING", "CAL_INVALID_BOUNDARIES",
                        "effectiveFrom (" + calendar.getEffectiveFrom() + ") cannot be after effectiveTo (" + calendar.getEffectiveTo() + ")",
                        calendar.getId());
            }
        }

        // Rule 2: Minimum 1 active term required for publication
        long activeTerms = terms.stream().filter(t -> t.getStatus() == TermStatus.ACTIVE).count();
        if (activeTerms == 0) {
            report.addIssue("BLOCKING", "CAL_NO_TERMS", "Calendar must have at least one active term before publication", calendar.getId());
        }

        // Rule 3: Term overlap & containment checks
        for (int i = 0; i < terms.size(); i++) {
            CalendarTerm t1 = terms.get(i);
            if (t1.getStatus() != TermStatus.ACTIVE) continue;

            // Start <= End
            if (t1.getStartDate().compareTo(t1.getEndDate()) > 0) {
                report.addIssue("BLOCKING", "TERM_INVALID_DATES",
                        "Term " + t1.getTermCode() + " startDate after endDate", t1.getId());
            }

            // Instructional dates containment
            if (t1.getInstructionalStartDate() != null && t1.getInstructionalStartDate().compareTo(t1.getStartDate()) < 0) {
                report.addIssue("BLOCKING", "TERM_INSTRUCTIONAL_START_OUT_OF_BOUNDS",
                        "Term " + t1.getTermCode() + " instructionalStartDate precedes term startDate", t1.getId());
            }
            if (t1.getInstructionalEndDate() != null && t1.getInstructionalEndDate().compareTo(t1.getEndDate()) > 0) {
                report.addIssue("BLOCKING", "TERM_INSTRUCTIONAL_END_OUT_OF_BOUNDS",
                        "Term " + t1.getTermCode() + " instructionalEndDate exceeds term endDate", t1.getId());
            }

            // Calendar boundary containment
            if (calendar.getEffectiveFrom() != null && t1.getStartDate().compareTo(calendar.getEffectiveFrom()) < 0) {
                report.addIssue("BLOCKING", "TERM_OUTSIDE_CALENDAR",
                        "Term " + t1.getTermCode() + " starts before calendar effectiveFrom", t1.getId());
            }
            if (calendar.getEffectiveTo() != null && t1.getEndDate().compareTo(calendar.getEffectiveTo()) > 0) {
                report.addIssue("BLOCKING", "TERM_OUTSIDE_CALENDAR",
                        "Term " + t1.getTermCode() + " ends after calendar effectiveTo", t1.getId());
            }

            // Check against remaining terms for overlap
            for (int j = i + 1; j < terms.size(); j++) {
                CalendarTerm t2 = terms.get(j);
                if (t2.getStatus() != TermStatus.ACTIVE) continue;
                if (datesOverlap(t1.getStartDate(), t1.getEndDate(), t2.getStartDate(), t2.getEndDate())) {
                    report.addIssue("BLOCKING", "ACD_CALENDAR_TERM_OVERLAP",
                            "Term '" + t1.getTermCode() + "' overlaps with term '" + t2.getTermCode() + "'", t1.getId());
                }
            }
        }

        // Rule 4: Event validation
        Map<String, List<CalendarEvent>> eventsByDate = new HashMap<>();
        for (CalendarEvent event : events) {
            if (event.getStatus() != TermStatus.ACTIVE) continue;

            if (event.getStartDate().compareTo(event.getEndDate()) > 0) {
                report.addIssue("BLOCKING", "EVENT_INVALID_DATES",
                        "Event " + event.getEventCode() + " startDate after endDate", event.getId());
            }

            // Event bounds vs calendar
            if (calendar.getEffectiveFrom() != null && event.getStartDate().compareTo(calendar.getEffectiveFrom()) < 0) {
                report.addIssue("WARNING", "EVENT_OUTSIDE_CALENDAR",
                        "Event " + event.getEventCode() + " starts before calendar effectiveFrom", event.getId());
            }
            if (calendar.getEffectiveTo() != null && event.getEndDate().compareTo(calendar.getEffectiveTo()) > 0) {
                report.addIssue("WARNING", "EVENT_OUTSIDE_CALENDAR",
                        "Event " + event.getEventCode() + " ends after calendar effectiveTo", event.getId());
            }

            eventsByDate.computeIfAbsent(event.getStartDate(), d -> new ArrayList<>()).add(event);
        }

        // Rule 5: Conflict detection between Holiday and Exams / Overrides
        for (Map.Entry<String, List<CalendarEvent>> entry : eventsByDate.entrySet()) {
            boolean hasHoliday = false;
            boolean hasExam = false;
            for (CalendarEvent ev : entry.getValue()) {
                if (ev.getEventType() == EventType.HOLIDAY) hasHoliday = true;
                if (ev.getEventType() == EventType.EXAM) hasExam = true;
            }
            if (hasHoliday && hasExam) {
                report.addIssue("WARNING", "HOLIDAY_EXAM_CLASH",
                        "Holiday and Examination scheduled on same date: " + entry.getKey(), entry.getKey());
            }
        }

        return report;
    }

    /**
     * Change Impact Analysis (US-022): analyzes downstream impacts of proposed calendar changes.
     */
    public Map<String, Object> analyzeImpact(AcademicCalendar proposed, AcademicCalendar currentPublished,
                                             List<CalendarTerm> proposedTerms, List<CalendarTerm> publishedTerms,
                                             List<CalendarEvent> proposedEvents, List<CalendarEvent> publishedEvents) {
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("calendarId", proposed.getId());
        impact.put("proposedVersion", proposed.getCurrentVersion());
        impact.put("publishedVersion", currentPublished != null ? currentPublished.getCurrentVersion() : null);

        List<String> affectedDownstream = new ArrayList<>();
        affectedDownstream.add("ACD-05 Timetable Management Service (Impacted teaching slots)");
        affectedDownstream.add("ACD-06 Attendance Management Service (Instantiated session validity)");
        affectedDownstream.add("EXM Examination Management Service (Exam scheduling slots)");

        impact.put("affectedDownstreamConsumers", affectedDownstream);

        int addedTerms = Math.max(0, proposedTerms.size() - publishedTerms.size());
        int addedEvents = Math.max(0, proposedEvents.size() - publishedEvents.size());

        impact.put("termCountDifference", addedTerms);
        impact.put("eventCountDifference", addedEvents);
        impact.put("classification", (addedTerms > 0 || currentPublished == null) ? "MAJOR_RESTRUCTURE" : "MINOR_ADJUSTMENT");

        return impact;
    }

    private boolean datesOverlap(String s1, String e1, String s2, String e2) {
        return s1.compareTo(e2) <= 0 && e1.compareTo(s2) >= 0;
    }
}
