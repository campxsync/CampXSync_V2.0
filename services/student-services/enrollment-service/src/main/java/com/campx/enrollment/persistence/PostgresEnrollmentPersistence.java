package com.campx.enrollment.persistence;

import com.campx.enrollment.Actor;
import com.campx.enrollment.EnrollmentDomainService.Course;
import com.campx.enrollment.EnrollmentDomainService.Enrolment;
import com.campx.enrollment.EnrollmentDomainService.Section;
import com.campx.enrollment.EnrollmentDomainService.Selection;
import com.campx.enrollment.EnrollmentDomainService.Term;
import com.campx.enrollment.EnrollmentDomainService.Window;
import com.campx.enrollment.EnrollmentException;
import com.campx.enrollment.config.DatabaseConnectionManager;
import com.campx.enrollment.security.EnrollmentSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link EnrollmentPersistence}.
 * Each call runs inside the caller's tenant row-level security context.
 * SQL failures are logged by {@link EnrollmentSecurityContext} and rolled back.
 */
public class PostgresEnrollmentPersistence implements EnrollmentPersistence {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresEnrollmentPersistence.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresEnrollmentPersistence() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresEnrollmentPersistence(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void saveTerm(Actor actor, final Term term) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.academic_terms (tenant_id, id, year_code, term_code, name) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "year_code = EXCLUDED.year_code, term_code = EXCLUDED.term_code, name = EXCLUDED.name";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, term.tenantId);
                    ps.setObject(2, term.id);
                    ps.setString(3, term.yearCode);
                    ps.setString(4, term.termCode);
                    ps.setString(5, term.name);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveWindow(Actor actor, final Window window) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.registration_windows "
                        + "(tenant_id, id, term_id, opens_at, closes_at, min_credits, max_credits) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "opens_at = EXCLUDED.opens_at, closes_at = EXCLUDED.closes_at, "
                        + "min_credits = EXCLUDED.min_credits, max_credits = EXCLUDED.max_credits";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, window.tenantId);
                    ps.setObject(2, window.id);
                    ps.setObject(3, window.termId);
                    ps.setTimestamp(4, Timestamp.from(window.opensAt));
                    ps.setTimestamp(5, Timestamp.from(window.closesAt));
                    ps.setInt(6, window.minCredits);
                    ps.setInt(7, window.maxCredits);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveCourse(Actor actor, final Course course) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.enrolment_courses "
                        + "(tenant_id, id, program_id, term_id, code, credits, is_core, prerequisites) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "code = EXCLUDED.code, credits = EXCLUDED.credits, is_core = EXCLUDED.is_core, "
                        + "prerequisites = EXCLUDED.prerequisites";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, course.tenantId);
                    ps.setObject(2, course.id);
                    ps.setObject(3, course.programId);
                    ps.setObject(4, course.termId);
                    ps.setString(5, course.code);
                    ps.setInt(6, course.credits);
                    ps.setBoolean(7, course.core);
                    UUID[] prereqs = course.prerequisites.toArray(new UUID[course.prerequisites.size()]);
                    Array array = conn.createArrayOf("uuid", prereqs);
                    ps.setArray(8, array);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveSection(Actor actor, final Section section) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.enrolment_sections "
                        + "(tenant_id, id, course_id, capacity, enrolled_count, faculty_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "capacity = EXCLUDED.capacity, enrolled_count = EXCLUDED.enrolled_count, "
                        + "faculty_user_id = EXCLUDED.faculty_user_id";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, section.tenantId);
                    ps.setObject(2, section.id);
                    ps.setObject(3, section.courseId);
                    ps.setInt(4, section.capacity);
                    ps.setInt(5, section.enrolledCount);
                    ps.setObject(6, section.facultyUserId);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveClearedCourse(Actor actor, final UUID studentId, final UUID courseId) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.student_cleared_courses (tenant_id, student_id, course_id) "
                        + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    ps.setObject(3, courseId);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void saveEnrolment(Actor actor, final Enrolment enrolment) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String upsert = "INSERT INTO stu.enrolments "
                        + "(tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, id) DO UPDATE SET "
                        + "status = EXCLUDED.status, total_credits = EXCLUDED.total_credits";
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setObject(1, enrolment.tenantId);
                    ps.setObject(2, enrolment.id);
                    ps.setObject(3, enrolment.applicationId);
                    ps.setObject(4, enrolment.studentId);
                    ps.setObject(5, enrolment.programId);
                    ps.setObject(6, enrolment.termId);
                    ps.setString(7, enrolment.status);
                    ps.setInt(8, enrolment.totalCredits);
                    ps.executeUpdate();
                }

                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM stu.enrolment_selections WHERE tenant_id = ? AND enrolment_id = ?")) {
                    delete.setObject(1, enrolment.tenantId);
                    delete.setObject(2, enrolment.id);
                    delete.executeUpdate();
                }

                if (enrolment.selections != null && !enrolment.selections.isEmpty()) {
                    String insertSelection = "INSERT INTO stu.enrolment_selections "
                            + "(tenant_id, enrolment_id, course_id, section_id) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSelection)) {
                        for (Selection selection : enrolment.selections) {
                            ps.setObject(1, enrolment.tenantId);
                            ps.setObject(2, enrolment.id);
                            ps.setObject(3, selection.courseId);
                            ps.setObject(4, selection.sectionId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                return null;
            }
        });
        logger.info("persisted enrolment id={} status={} tenantId={}", enrolment.id, enrolment.status, enrolment.tenantId);
    }

    @Override
    public void appendHistory(Actor actor, final Enrolment enrolment, final Map<String, String> historyRow) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "INSERT INTO stu.enrolment_status_history "
                        + "(tenant_id, enrolment_id, status, action, actor_role, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, enrolment.tenantId);
                    ps.setObject(2, enrolment.id);
                    ps.setString(3, historyRow.get("status"));
                    ps.setString(4, historyRow.get("action"));
                    ps.setString(5, historyRow.get("actorRole"));
                    String at = historyRow.get("at");
                    ps.setTimestamp(6, Timestamp.from(at == null ? Instant.now() : Instant.parse(at)));
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void replaceWaitlist(Actor actor, final Section section) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM stu.section_waitlist WHERE tenant_id = ? AND section_id = ?")) {
                    delete.setObject(1, section.tenantId);
                    delete.setObject(2, section.id);
                    delete.executeUpdate();
                }
                String insert = "INSERT INTO stu.section_waitlist (tenant_id, section_id, student_id, position) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (int i = 0; i < section.waitlist.size(); i++) {
                        ps.setObject(1, section.tenantId);
                        ps.setObject(2, section.id);
                        ps.setObject(3, section.waitlist.get(i));
                        ps.setInt(4, i + 1);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE stu.enrolment_sections SET enrolled_count = ? WHERE tenant_id = ? AND id = ?")) {
                    update.setInt(1, section.enrolledCount);
                    update.setObject(2, section.tenantId);
                    update.setObject(3, section.id);
                    update.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public Enrolment findEnrolment(Actor actor, final UUID enrolmentId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Enrolment>() {
            @Override
            public Enrolment execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits "
                        + "FROM stu.enrolments WHERE tenant_id = ? AND id = ?";
                Enrolment enrolment;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, enrolmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("Enrolment was not found id={}", enrolmentId);
                            throw new EnrollmentException(404, "ENROLMENT_NOT_FOUND", "Enrolment was not found");
                        }
                        enrolment = new Enrolment(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("tenant_id"),
                                (UUID) rs.getObject("application_id"),
                                (UUID) rs.getObject("student_id"),
                                (UUID) rs.getObject("program_id"),
                                (UUID) rs.getObject("term_id"),
                                rs.getString("status"));
                        enrolment.totalCredits = rs.getInt("total_credits");
                    }
                }

                String selectionsSql = "SELECT course_id, section_id FROM stu.enrolment_selections "
                        + "WHERE tenant_id = ? AND enrolment_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectionsSql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, enrolmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            enrolment.selections.add(new Selection(
                                    (UUID) rs.getObject("course_id"),
                                    (UUID) rs.getObject("section_id")));
                        }
                    }
                }
                return enrolment;
            }
        });
    }

    @Override
    public List<Enrolment> findEnrolmentsForStudent(Actor actor, final UUID studentId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Enrolment>>() {
            @Override
            public List<Enrolment> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits "
                        + "FROM stu.enrolments WHERE tenant_id = ? AND student_id = ? ORDER BY created_at DESC";
                List<Enrolment> rows = new ArrayList<Enrolment>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Enrolment enrolment = new Enrolment(
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("application_id"),
                                    (UUID) rs.getObject("student_id"),
                                    (UUID) rs.getObject("program_id"),
                                    (UUID) rs.getObject("term_id"),
                                    rs.getString("status"));
                            enrolment.totalCredits = rs.getInt("total_credits");
                            rows.add(enrolment);
                        }
                    }
                }
                for (Enrolment enrolment : rows) {
                    String selectionsSql = "SELECT course_id, section_id FROM stu.enrolment_selections "
                            + "WHERE tenant_id = ? AND enrolment_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(selectionsSql)) {
                        ps.setObject(1, actor.getTenantId());
                        ps.setObject(2, enrolment.id);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                enrolment.selections.add(new Selection(
                                        (UUID) rs.getObject("course_id"),
                                        (UUID) rs.getObject("section_id")));
                            }
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<Map<String, String>> findHistory(Actor actor, final UUID enrolmentId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> execute(Connection conn) throws SQLException {
                String sql = "SELECT status, action, actor_role, occurred_at FROM stu.enrolment_status_history "
                        + "WHERE tenant_id = ? AND enrolment_id = ? ORDER BY occurred_at ASC";
                List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, enrolmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> row = new LinkedHashMap<String, String>();
                            row.put("status", rs.getString("status"));
                            row.put("action", rs.getString("action"));
                            row.put("actorRole", rs.getString("actor_role"));
                            Timestamp ts = rs.getTimestamp("occurred_at");
                            row.put("at", ts == null ? "" : ts.toInstant().toString());
                            rows.add(row);
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public Integer findWaitlistPosition(Actor actor, final UUID sectionId, final UUID studentId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Integer>() {
            @Override
            public Integer execute(Connection conn) throws SQLException {
                String sql = "SELECT position FROM stu.section_waitlist WHERE tenant_id = ? AND section_id = ? AND student_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, sectionId);
                    ps.setObject(3, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return rs.getInt("position");
                    }
                }
            }
        });
    }

    @Override
    public boolean tryReserveSeat(Actor actor, final UUID sectionId) {
        Boolean reserved = EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Boolean>() {
            @Override
            public Boolean execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.enrolment_sections SET enrolled_count = enrolled_count + 1 "
                        + "WHERE tenant_id = ? AND id = ? AND enrolled_count < capacity";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, sectionId);
                    return ps.executeUpdate() == 1;
                }
            }
        });
        return Boolean.TRUE.equals(reserved);
    }

    @Override
    public List<Term> findTerms(Actor actor) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Term>>() {
            @Override
            public List<Term> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, year_code, term_code, name FROM stu.academic_terms WHERE tenant_id = ? ORDER BY created_at DESC";
                List<Term> rows = new ArrayList<Term>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new Term((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                                    rs.getString("year_code"), rs.getString("term_code"), rs.getString("name")));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public Window findWindow(Actor actor, final UUID termId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Window>() {
            @Override
            public Window execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, term_id, opens_at, closes_at, min_credits, max_credits "
                        + "FROM stu.registration_windows WHERE tenant_id = ? AND term_id = ? ORDER BY opens_at DESC LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, termId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return new Window((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("term_id"),
                                rs.getTimestamp("opens_at").toInstant(), rs.getTimestamp("closes_at").toInstant(),
                                rs.getInt("min_credits"), rs.getInt("max_credits"));
                    }
                }
            }
        });
    }

    @Override
    public List<Course> findCourses(Actor actor, final UUID programId, final UUID termId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Course>>() {
            @Override
            public List<Course> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, program_id, term_id, code, credits, is_core, prerequisites "
                        + "FROM stu.enrolment_courses WHERE tenant_id = ? AND program_id = ? AND term_id = ?";
                List<Course> rows = new ArrayList<Course>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, programId);
                    ps.setObject(3, termId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            List<UUID> prerequisites = new ArrayList<UUID>();
                            Array array = rs.getArray("prerequisites");
                            if (array != null) {
                                Object[] values = (Object[]) array.getArray();
                                for (Object value : values) {
                                    if (value != null) {
                                        prerequisites.add(UUID.fromString(value.toString()));
                                    }
                                }
                            }
                            rows.add(new Course((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("program_id"), (UUID) rs.getObject("term_id"),
                                    rs.getString("code"), rs.getInt("credits"), rs.getBoolean("is_core"), prerequisites));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<Section> findSections(Actor actor, final UUID courseId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Section>>() {
            @Override
            public List<Section> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, course_id, capacity, enrolled_count, faculty_user_id "
                        + "FROM stu.enrolment_sections WHERE tenant_id = ? AND course_id = ?";
                List<Section> rows = new ArrayList<Section>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, courseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new Section((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("course_id"), rs.getInt("capacity"), rs.getInt("enrolled_count"),
                                    (UUID) rs.getObject("faculty_user_id")));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<UUID> findClearedCourses(Actor actor, final UUID studentId) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<UUID>>() {
            @Override
            public List<UUID> execute(Connection conn) throws SQLException {
                String sql = "SELECT course_id FROM stu.student_cleared_courses WHERE tenant_id = ? AND student_id = ?";
                List<UUID> rows = new ArrayList<UUID>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add((UUID) rs.getObject("course_id"));
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<Enrolment> findEnrolmentsForTenant(Actor actor) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Enrolment>>() {
            @Override
            public List<Enrolment> execute(Connection conn) throws SQLException {
                String sql = "SELECT tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits "
                        + "FROM stu.enrolments WHERE tenant_id = ? ORDER BY created_at DESC";
                List<Enrolment> rows = new ArrayList<Enrolment>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Enrolment enrolment = new Enrolment(
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("application_id"),
                                    (UUID) rs.getObject("student_id"),
                                    (UUID) rs.getObject("program_id"),
                                    (UUID) rs.getObject("term_id"),
                                    rs.getString("status"));
                            enrolment.totalCredits = rs.getInt("total_credits");
                            rows.add(enrolment);
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public List<Map<String, String>> findFacultyRosterRows(Actor actor) {
        return EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> execute(Connection conn) throws SQLException {
                String sql = "SELECT s.id AS section_id, c.code AS course_code, s.capacity, s.enrolled_count, e.student_id "
                        + "FROM stu.enrolment_sections s "
                        + "JOIN stu.enrolment_courses c ON c.tenant_id = s.tenant_id AND c.id = s.course_id "
                        + "LEFT JOIN stu.enrolment_selections sel ON sel.tenant_id = s.tenant_id AND sel.section_id = s.id "
                        + "LEFT JOIN stu.enrolments e ON e.tenant_id = sel.tenant_id AND e.id = sel.enrolment_id AND e.status = 'ENROLLED' "
                        + "WHERE s.tenant_id = ? AND s.faculty_user_id = ? "
                        + "ORDER BY c.code, s.id";
                List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, actor.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> row = new LinkedHashMap<String, String>();
                            row.put("sectionId", rs.getObject("section_id").toString());
                            row.put("courseCode", rs.getString("course_code"));
                            row.put("capacity", Integer.toString(rs.getInt("capacity")));
                            row.put("enrolledCount", Integer.toString(rs.getInt("enrolled_count")));
                            Object studentId = rs.getObject("student_id");
                            row.put("studentId", studentId == null ? "" : studentId.toString());
                            rows.add(row);
                        }
                    }
                }
                return rows;
            }
        });
    }

    @Override
    public void releaseSeat(Actor actor, final UUID sectionId) {
        EnrollmentSecurityContext.fromActor(actor).executeInTransaction(connectionManager, new EnrollmentSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException {
                String sql = "UPDATE stu.enrolment_sections SET enrolled_count = GREATEST(enrolled_count - 1, 0) "
                        + "WHERE tenant_id = ? AND id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, actor.getTenantId());
                    ps.setObject(2, sectionId);
                    ps.executeUpdate();
                }
                return null;
            }
        });
    }
}
