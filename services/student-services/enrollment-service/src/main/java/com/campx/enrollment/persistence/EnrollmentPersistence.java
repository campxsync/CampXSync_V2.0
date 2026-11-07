package com.campx.enrollment.persistence;

import com.campx.enrollment.Actor;
import com.campx.enrollment.EnrollmentDomainService.Course;
import com.campx.enrollment.EnrollmentDomainService.Enrolment;
import com.campx.enrollment.EnrollmentDomainService.Section;
import com.campx.enrollment.EnrollmentDomainService.Term;
import com.campx.enrollment.EnrollmentDomainService.Window;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional committed persistence for STM-03 tables under stu.*.
 * Unit tests run without an implementation (in-memory domain only).
 */
public interface EnrollmentPersistence {

    void saveTerm(Actor actor, Term term);

    void saveWindow(Actor actor, Window window);

    void saveCourse(Actor actor, Course course);

    void saveSection(Actor actor, Section section);

    void saveClearedCourse(Actor actor, UUID studentId, UUID courseId);

    void saveEnrolment(Actor actor, Enrolment enrolment);

    void appendHistory(Actor actor, Enrolment enrolment, Map<String, String> historyRow);

    void replaceWaitlist(Actor actor, Section section);

    Enrolment findEnrolment(Actor actor, UUID enrolmentId);

    List<Enrolment> findEnrolmentsForStudent(Actor actor, UUID studentId);

    List<Map<String, String>> findHistory(Actor actor, UUID enrolmentId);

    Integer findWaitlistPosition(Actor actor, UUID sectionId, UUID studentId);

    /**
     * Atomically increments enrolled_count when capacity remains.
     * @return true if seat reserved
     */
    boolean tryReserveSeat(Actor actor, UUID sectionId);

    void releaseSeat(Actor actor, UUID sectionId);

    List<Term> findTerms(Actor actor);

    Window findWindow(Actor actor, UUID termId);

    List<Course> findCourses(Actor actor, UUID programId, UUID termId);

    List<Section> findSections(Actor actor, UUID courseId);

    List<UUID> findClearedCourses(Actor actor, UUID studentId);

    List<Enrolment> findEnrolmentsForTenant(Actor actor);

    /**
     * One row per assigned section, repeated once per enrolled student.
     * studentId is empty when the section has no enrolled student.
     */
    List<Map<String, String>> findFacultyRosterRows(Actor actor);
}
