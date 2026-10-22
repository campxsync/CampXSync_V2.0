package com.campx.student.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import com.campx.platform.outbox.OutboxRepository;
import com.campx.student.exception.StudentConflictException;
import com.campx.student.exception.StudentLifecycleException;
import com.campx.student.exception.StudentNotFoundException;
import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.*;
import com.campx.student.repository.GuardianshipRepository;
import com.campx.student.repository.PersonRepository;
import com.campx.student.repository.StudentRepository;
import com.campx.student.security.UserSecurityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service governing student matriculation, canonical record updates,
 * lifecycle state machine enforcement, and transactional outbox event publication.
 */
public class StudentDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentDomainService.class);
    private static final String SERVICE_NAME = "student-service";
    private static final String AGGREGATE_TYPE = "STUDENT";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final StudentRepository studentRepository;
    private final PersonRepository personRepository;
    private final GuardianshipRepository guardianshipRepository;
    private final StudentEventPublisher eventPublisher;

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository) {
        this(studentRepository, personRepository, guardianshipRepository, new StudentEventPublisher.InMemory());
    }

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                StudentEventPublisher eventPublisher) {
        if (studentRepository == null) throw new IllegalArgumentException("studentRepository cannot be null");
        if (personRepository == null) throw new IllegalArgumentException("personRepository cannot be null");
        if (guardianshipRepository == null) throw new IllegalArgumentException("guardianshipRepository cannot be null");
        this.studentRepository = studentRepository;
        this.personRepository = personRepository;
        this.guardianshipRepository = guardianshipRepository;
        this.eventPublisher = eventPublisher != null ? eventPublisher : new StudentEventPublisher.InMemory();
    }

    public StudentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    /**
     * Enrolls a student: creates canonical person, student, optional guardianship,
     * emits batch assignment contract for Academics tier, and publishes outbox event.
     *
     * @param context Active authenticated security context.
     * @param request Ingress enrollment DTO.
     * @return Complete composite student profile response.
     */
    public StudentProfileResponse enrollStudent(UserSecurityContext context, EnrollStudentRequest request) {
        validateEnrollmentRequest(request);

        // 1. Check duplicate enrollment_no in tenant
        if (studentRepository.existsByEnrollmentNo(context, request.getEnrollmentNo())) {
            throw new StudentConflictException(String.format(
                    "Student with enrollment number '%s' already exists in tenant.", request.getEnrollmentNo()));
        }

        // 2. Check duplicate primary_email in tenant
        if (personRepository.existsByEmail(context, request.getPrimaryEmail())) {
            throw new StudentConflictException(String.format(
                    "Person with email '%s' already exists in tenant.", request.getPrimaryEmail()));
        }

        // 3. Create core.persons record
        PersonEntity person = new PersonEntity();
        person.setFirstName(request.getFirstName().trim());
        person.setMiddleName(request.getMiddleName() != null ? request.getMiddleName().trim() : null);
        person.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        person.setDisplayName(request.getDisplayName());
        person.setPrimaryEmail(request.getPrimaryEmail().trim().toLowerCase());
        person.setPrimaryPhone(request.getPrimaryPhone() != null ? request.getPrimaryPhone().trim() : null);
        person.setGender(request.getGender() != null ? request.getGender() : Gender.U);
        person.setDateOfBirth(request.getParsedDateOfBirth());
        person = personRepository.save(context, person);

        // 4. Create stu.students record
        StudentEntity student = new StudentEntity();
        student.setPersonId(person.getId());
        student.setCollegeId(request.getCollegeId());
        student.setProgramId(request.getProgramId());
        student.setEnrollmentNo(request.getEnrollmentNo().trim());
        student.setAdmissionNo(request.getAdmissionNo() != null ? request.getAdmissionNo().trim() : null);
        student.setAdmissionDate(request.getParsedAdmissionDate());
        student.setAdmissionAcademicYearId(request.getAdmissionAcademicYearId());
        student.setStatus(StudentStatus.ACTIVE);
        student = studentRepository.save(context, student);

        // 5. Optional primary guardian creation
        List<StudentProfileResponse.GuardianSummary> guardianSummaries = new ArrayList<>();
        if (request.getGuardian() != null) {
            EnrollStudentRequest.GuardianRequest gReq = request.getGuardian();
            if (gReq.getFirstName() != null && !gReq.getFirstName().trim().isEmpty()) {
                PersonEntity guardianPerson = new PersonEntity();
                guardianPerson.setFirstName(gReq.getFirstName().trim());
                guardianPerson.setLastName(gReq.getLastName() != null ? gReq.getLastName().trim() : null);
                String gDisplayName = (gReq.getFirstName().trim() + " " + (gReq.getLastName() != null ? gReq.getLastName().trim() : "")).trim();
                guardianPerson.setDisplayName(gDisplayName);
                guardianPerson.setPrimaryEmail(gReq.getPrimaryEmail() != null ? gReq.getPrimaryEmail().trim().toLowerCase() : null);
                guardianPerson.setPrimaryPhone(gReq.getPrimaryPhone() != null ? gReq.getPrimaryPhone().trim() : null);
                guardianPerson = personRepository.save(context, guardianPerson);

                GuardianshipEntity guardianship = new GuardianshipEntity();
                guardianship.setStudentId(student.getId());
                guardianship.setGuardianPersonId(guardianPerson.getId());
                guardianship.setRelationship(gReq.getRelationship() != null ? gReq.getRelationship().trim().toUpperCase() : "GUARDIAN");
                guardianship.setPrimary(gReq.isPrimary());
                guardianship.setCanViewAcademics(gReq.isCanViewAcademics());
                guardianship.setCanViewFees(gReq.isCanViewFees());
                guardianship.setCanPayFees(gReq.isCanPayFees());
                guardianship = guardianshipRepository.save(context, guardianship);

                StudentProfileResponse.GuardianSummary summary = new StudentProfileResponse.GuardianSummary();
                summary.setGuardianshipId(guardianship.getId());
                summary.setGuardianPersonId(guardianPerson.getId());
                summary.setDisplayName(guardianPerson.getDisplayName());
                summary.setRelationship(guardianship.getRelationship());
                summary.setPrimary(guardianship.isPrimary());
                summary.setPrimaryPhone(guardianPerson.getPrimaryPhone());
                summary.setPrimaryEmail(guardianPerson.getPrimaryEmail());
                guardianSummaries.add(summary);
            }
        }

        // 6. Decoupled contract for Academics domain (ACD-04) if batch requested
        BatchAssignmentContract batchContract = null;
        if (request.getBatchId() != null) {
            batchContract = new BatchAssignmentContract(
                    context.getTenantId(),
                    request.getCollegeId(),
                    student.getId(),
                    request.getBatchId(),
                    request.getRollNo(),
                    "ASSIGN"
            );
            logger.info("Emitted BatchAssignmentContract for Academics domain: {}", batchContract);
        }

        // 7. Publish transactional outbox event: student.enrolled.v1
        publishOutboxEvent(context, student.getId(), "student.enrolled.v1", String.format(
                "{\"studentId\":\"%s\",\"personId\":\"%s\",\"enrollmentNo\":\"%s\",\"programId\":\"%s\",\"collegeId\":\"%s\",\"status\":\"%s\"}",
                student.getId(), person.getId(), student.getEnrollmentNo(),
                student.getProgramId() != null ? student.getProgramId() : "",
                student.getCollegeId(), student.getStatus()
        ));

        logger.info("Enrolled student [{}] with enrollment_no [{}] in tenant [{}]",
                student.getId(), student.getEnrollmentNo(), context.getTenantId());

        return buildProfileResponse(student, person, guardianSummaries);
    }

    /**
     * Executes a lifecycle state transition enforcing allowed transitions.
     *
     * @param context Active authenticated security context.
     * @param studentId Target student ID.
     * @param request Target status and reason.
     * @return Updated student profile response.
     */
    public StudentProfileResponse transitionLifecycle(UserSecurityContext context, UUID studentId, UpdateStudentStatusRequest request) {
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getStatus() == null) {
            throw new StudentValidationException("Target student status is required");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        StudentStatus current = student.getStatus();
        StudentStatus target = request.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new StudentLifecycleException(String.format(
                    "Illegal state transition from '%s' to '%s' for student '%s'.",
                    current, target, student.getEnrollmentNo()));
        }

        student.setStatus(target);
        student = studentRepository.save(context, student);

        // Publish outbox event: student.status_changed.v1
        publishOutboxEvent(context, student.getId(), "student.status_changed.v1", String.format(
                "{\"studentId\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"reason\":\"%s\"}",
                student.getId(), current, target,
                request.getReason() != null ? request.getReason().replace("\"", "\\\"") : ""
        ));

        logger.info("Transitioned student [{}] from [{}] to [{}]", studentId, current, target);

        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);
        List<GuardianshipEntity> guardians = guardianshipRepository.findByStudentId(context, student.getId());
        List<StudentProfileResponse.GuardianSummary> summaries = mapGuardianSummaries(context, guardians);

        return buildProfileResponse(student, person, summaries);
    }

    /**
     * Retrieves a cursor-paginated roster of students.
     */
    public CursorPagedResult<StudentRosterItem> getRoster(UserSecurityContext context, StudentRosterQuery query) {
        return studentRepository.findRoster(context, query);
    }

    /**
     * Retrieves the complete student composite profile by ID.
     */
    public StudentProfileResponse getStudentById(UserSecurityContext context, UUID studentId) {
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);
        List<GuardianshipEntity> guardians = guardianshipRepository.findByStudentId(context, student.getId());
        List<StudentProfileResponse.GuardianSummary> summaries = mapGuardianSummaries(context, guardians);

        return buildProfileResponse(student, person, summaries);
    }

    /**
     * Adds or updates a guardian relationship for an existing student.
     */
    public StudentProfileResponse addGuardian(UserSecurityContext context, UUID studentId, EnrollStudentRequest.GuardianRequest gReq) {
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (gReq == null || gReq.getFirstName() == null || gReq.getFirstName().trim().isEmpty()) {
            throw new StudentValidationException("Guardian first name is required");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        PersonEntity guardianPerson = new PersonEntity();
        guardianPerson.setFirstName(gReq.getFirstName().trim());
        guardianPerson.setLastName(gReq.getLastName() != null ? gReq.getLastName().trim() : null);
        String gDisplayName = (gReq.getFirstName().trim() + " " + (gReq.getLastName() != null ? gReq.getLastName().trim() : "")).trim();
        guardianPerson.setDisplayName(gDisplayName);
        guardianPerson.setPrimaryEmail(gReq.getPrimaryEmail() != null ? gReq.getPrimaryEmail().trim().toLowerCase() : null);
        guardianPerson.setPrimaryPhone(gReq.getPrimaryPhone() != null ? gReq.getPrimaryPhone().trim() : null);
        guardianPerson = personRepository.save(context, guardianPerson);

        GuardianshipEntity guardianship = new GuardianshipEntity();
        guardianship.setStudentId(student.getId());
        guardianship.setGuardianPersonId(guardianPerson.getId());
        guardianship.setRelationship(gReq.getRelationship() != null ? gReq.getRelationship().trim().toUpperCase() : "GUARDIAN");
        guardianship.setPrimary(gReq.isPrimary());
        guardianship.setCanViewAcademics(gReq.isCanViewAcademics());
        guardianship.setCanViewFees(gReq.isCanViewFees());
        guardianship.setCanPayFees(gReq.isCanPayFees());
        guardianshipRepository.save(context, guardianship);

        return getStudentById(context, studentId);
    }

    /**
     * Soft-deletes and deactivates a student record within the tenant.
     *
     * @param context Active authenticated security context.
     * @param studentId ID of student to deactivate.
     */
    public void deactivateStudent(UserSecurityContext context, UUID studentId) {
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        studentRepository.delete(context, studentId);
        logger.info("Deactivated student [{}] ({}) in tenant [{}]", studentId, student.getEnrollmentNo(), context.getTenantId());
    }

    private void validateEnrollmentRequest(EnrollStudentRequest req) {
        if (req == null) throw new StudentValidationException("EnrollStudentRequest cannot be null");
        if (req.getFirstName() == null || req.getFirstName().trim().isEmpty()) {
            throw new StudentValidationException("Student first name is required");
        }
        if (req.getPrimaryEmail() == null || req.getPrimaryEmail().trim().isEmpty() || !req.getPrimaryEmail().contains("@")) {
            throw new StudentValidationException("Valid student primary email is required");
        }
        if (req.getEnrollmentNo() == null || req.getEnrollmentNo().trim().length() < 3) {
            throw new StudentValidationException("Enrollment number must be at least 3 characters");
        }
        if (req.getCollegeId() == null) {
            throw new StudentValidationException("College ID is required for student matriculation");
        }
    }

    private void publishOutboxEvent(UserSecurityContext context, UUID studentId, String eventType, String payload) {
        if (eventPublisher == null) return;
        try {
            OutboxHeaders headers = OutboxHeaders.builder()
                    .correlationId(UUID.randomUUID().toString())
                    .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                    .schemaVersion(SCHEMA_VERSION)
                    .build();

            OutboxEvent event = OutboxEvent.builder()
                    .tenantId(context.getTenantId())
                    .service(SERVICE_NAME)
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(studentId)
                    .eventType(eventType)
                    .headers(headers)
                    .payload(payload)
                    .build();

            eventPublisher.publish(context, event);
        } catch (Exception e) {
            logger.error("Failed to publish transactional outbox event [{}]: {}", eventType, e.getMessage(), e);
        }
    }

    private StudentProfileResponse buildProfileResponse(StudentEntity student, PersonEntity person,
                                                        List<StudentProfileResponse.GuardianSummary> guardians) {
        StudentProfileResponse resp = new StudentProfileResponse();
        resp.setId(student.getId());
        resp.setTenantId(student.getTenantId());
        resp.setPersonId(student.getPersonId());
        resp.setCollegeId(student.getCollegeId());
        resp.setProgramId(student.getProgramId());
        resp.setEnrollmentNo(student.getEnrollmentNo());
        resp.setAdmissionNo(student.getAdmissionNo());
        resp.setAdmissionDate(student.getAdmissionDate());
        resp.setAdmissionAcademicYearId(student.getAdmissionAcademicYearId());
        resp.setStatus(student.getStatus());
        resp.setCreatedAt(student.getCreatedAt());
        resp.setUpdatedAt(student.getUpdatedAt());

        if (person != null) {
            resp.setFirstName(person.getFirstName());
            resp.setMiddleName(person.getMiddleName());
            resp.setLastName(person.getLastName());
            resp.setDisplayName(person.getDisplayName());
            resp.setPrimaryEmail(person.getPrimaryEmail());
            resp.setPrimaryPhone(person.getPrimaryPhone());
            resp.setGender(person.getGender());
            resp.setDateOfBirth(person.getDateOfBirth());
        }

        if (guardians != null) {
            resp.setGuardians(guardians);
        }

        return resp;
    }

    private List<StudentProfileResponse.GuardianSummary> mapGuardianSummaries(UserSecurityContext context, List<GuardianshipEntity> list) {
        List<StudentProfileResponse.GuardianSummary> summaries = new ArrayList<>();
        if (list == null) return summaries;
        for (GuardianshipEntity g : list) {
            StudentProfileResponse.GuardianSummary s = new StudentProfileResponse.GuardianSummary();
            s.setGuardianshipId(g.getId());
            s.setGuardianPersonId(g.getGuardianPersonId());
            s.setRelationship(g.getRelationship());
            s.setPrimary(g.isPrimary());

            if (g.getGuardianPersonId() != null) {
                personRepository.findById(context, g.getGuardianPersonId()).ifPresent(gp -> {
                    s.setDisplayName(gp.getDisplayName());
                    s.setPrimaryPhone(gp.getPrimaryPhone());
                    s.setPrimaryEmail(gp.getPrimaryEmail());
                });
            }
            summaries.add(s);
        }
        return summaries;
    }
}
