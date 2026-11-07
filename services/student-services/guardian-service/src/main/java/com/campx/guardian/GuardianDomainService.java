package com.campx.guardian;

import com.campx.guardian.intelligence.GuardianIntelligenceService;
import com.campx.guardian.persistence.GuardianPersistence;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STM-05 guardian authority.
 * <p>
 * A registrar, including a college or institution administrator, adds a parent or guardian
 * for a student in the same tenant. Students cannot create the link. The relationship is stored
 * in {@code stu.guardianships}. A successful add publishes {@code student.guardian.linked.v1}
 * for the Intelligence Layer.
 */
public class GuardianDomainService {

    public static final String GUARDIAN_LINKED = "student.guardian.linked.v1";
    public static final String GUARDIAN_UPDATED = "student.guardian.updated.v1";
    public static final String SCHEMA_VERSION = "1.0.0";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianDomainService.class);

    private final Clock clock;
    private final GuardianPersistence persistence;
    private final GuardianIntelligenceService intelligenceService;
    private final Map<String, List<GuardianLink>> links = new ConcurrentHashMap<String, List<GuardianLink>>();

    public GuardianDomainService() {
        this(Clock.systemUTC(), null, null);
    }

    public GuardianDomainService(Clock clock, GuardianPersistence persistence, GuardianIntelligenceService intelligenceService) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.persistence = persistence;
        this.intelligenceService = intelligenceService == null ? new GuardianIntelligenceService() : intelligenceService;
    }

    public boolean isPersistent() {
        return persistence != null;
    }

    /**
     * Adds a parent or guardian for a student (STM-05-US-001).
     *
     * @throws GuardianException 403 when the caller is not a registrar
     * @throws GuardianException 422 when the student, name, or relationship is missing
     */
    public GuardianLink addGuardian(Actor actor, UUID studentId, String firstName, String lastName,
                                    String relationship, String email, String phone, boolean primary) {
        requireRegistrar(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        requireText(firstName, "firstName");
        requireText(relationship, "relationship");
        Instant now = clock.instant();
        GuardianLink link = new GuardianLink(actor.getTenantId(), UUID.randomUUID(), studentId, UUID.randomUUID(),
                firstName.trim(), lastName == null ? "" : lastName.trim(), relationship.trim().toUpperCase(),
                email == null ? "" : email.trim(), phone == null ? "" : phone.trim(), primary, "ACTIVE", now);
        String key = statusKey(actor.getTenantId(), studentId);
        List<GuardianLink> rows = links.get(key);
        if (rows == null) {
            rows = Collections.synchronizedList(new ArrayList<GuardianLink>());
            links.put(key, rows);
        }
        rows.add(link);
        if (persistence != null) {
            persistence.saveGuardian(actor, link);
        }
        log(actor, "guardian.linked", link.id);
        intelligenceService.publishGuardianLinked(actor, link);
        return link;
    }

    /**
     * Updates the relationship and primary flag on an existing guardianship (STM-05-US-002).
     * Contact details stay on {@code core.persons}. This method does not write that table.
     *
     * @throws GuardianException 403 when the caller cannot update this student's guardian
     * @throws GuardianException 404 when the guardianship is not in the caller's tenant
     */
    public GuardianLink updateGuardian(Actor actor, UUID guardianshipId, String relationship, boolean primary) {
        requireTenant(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(relationship, "relationship");
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(current.studentId)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Students may update only their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Updating a guardian requires the student or a registrar");
        }
        GuardianLink updated = new GuardianLink(current.tenantId, current.id, current.studentId, current.guardianPersonId,
                current.firstName, current.lastName, relationship.trim().toUpperCase(), current.email, current.phone,
                primary, current.status, clock.instant());
        replaceLink(current, updated);
        if (persistence != null) {
            persistence.updateGuardian(actor, updated);
        }
        log(actor, "guardian.updated", updated.id);
        intelligenceService.publishGuardianUpdated(actor, current, updated);
        return updated;
    }

    /**
     * Lists guardians already linked to a student in the caller's tenant.
     * A student may list only their own record. A registrar may list any student in the tenant.
     *
     * @throws GuardianException 403 when a student asks for another student's guardians
     */
    public List<GuardianLink> listGuardians(Actor actor, UUID studentId) {
        requireTenant(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "GUARDIAN_VIEW_FORBIDDEN", "Students may list only their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "GUARDIAN_VIEW_FORBIDDEN", "Guardian view requires the student or a registrar");
        }
        if (persistence != null) {
            return persistence.findGuardians(actor, studentId);
        }
        List<GuardianLink> rows = links.get(statusKey(actor.getTenantId(), studentId));
        if (rows == null) {
            return Collections.emptyList();
        }
        return new ArrayList<GuardianLink>(rows);
    }

    private void requireRegistrar(Actor actor) {
        requireTenant(actor);
        if (!isRegistrar(actor)) {
            throw reject(403, "REGISTRAR_FORBIDDEN", "Adding a guardian requires catalogue role R31 Registrar");
        }
    }

    private void requireTenant(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw reject(422, "FIELD_REQUIRED", field + " is required");
        }
    }

    private boolean isRegistrar(Actor actor) {
        String role = actor.getRole();
        return "R31".equals(role) || "REGISTRAR".equals(role)
                || "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
    }

    private boolean isStudent(Actor actor) {
        return "R25".equals(actor.getRole()) || "STUDENT".equals(actor.getRole());
    }

    private GuardianException reject(int status, String code, String message) {
        if (status >= 500) {
            logger.error("stm05 rejected status={} code={} message={}", status, code, message);
        } else {
            logger.warn("stm05 rejected status={} code={} message={}", status, code, message);
        }
        return new GuardianException(status, code, message);
    }

    private void log(Actor actor, String action, UUID resourceId) {
        logger.info("stm05 action={} tenantId={} correlationId={} resourceId={}",
                action, actor.getTenantId(), actor.getCorrelationId(), resourceId);
        logger.audit(AuditEvent.builder()
                .action(action)
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId() == null ? null : actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("GUARDIAN")
                .resourceId(resourceId == null ? null : resourceId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description(action)
                .build());
    }

    private GuardianLink findLink(Actor actor, UUID guardianshipId) {
        if (persistence != null) {
            return persistence.findGuardian(actor, guardianshipId);
        }
        for (List<GuardianLink> rows : links.values()) {
            for (GuardianLink link : rows) {
                if (link.id.equals(guardianshipId) && link.tenantId.equals(actor.getTenantId())) {
                    return link;
                }
            }
        }
        return null;
    }

    private void replaceLink(GuardianLink current, GuardianLink updated) {
        List<GuardianLink> rows = links.get(statusKey(current.tenantId, current.studentId));
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(current.id)) {
                rows.set(i, updated);
                return;
            }
        }
    }

    private static String statusKey(UUID tenantId, UUID studentId) {
        return tenantId + ":" + studentId;
    }

    /**
     * One guardian relationship for a student.
     */
    public static final class GuardianLink {
        public final UUID tenantId;
        public final UUID id;
        public final UUID studentId;
        public final UUID guardianPersonId;
        public final String firstName;
        public final String lastName;
        public final String relationship;
        public final String email;
        public final String phone;
        public final boolean primary;
        public final String status;
        public final Instant linkedAt;

        public GuardianLink(UUID tenantId, UUID id, UUID studentId, UUID guardianPersonId, String firstName,
                            String lastName, String relationship, String email, String phone, boolean primary,
                            String status, Instant linkedAt) {
            this.tenantId = tenantId;
            this.id = id;
            this.studentId = studentId;
            this.guardianPersonId = guardianPersonId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.relationship = relationship;
            this.email = email;
            this.phone = phone;
            this.primary = primary;
            this.status = status;
            this.linkedAt = linkedAt;
        }
    }
}
