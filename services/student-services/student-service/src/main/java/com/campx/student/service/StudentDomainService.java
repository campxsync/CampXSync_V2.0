package com.campx.student.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.exception.*;
import com.campx.student.model.*;
import com.campx.student.repository.*;
import com.campx.student.security.UserSecurityContext;
import com.campx.platform.audit.AuditChangeLog;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.audit.PostgresAuditRepository;
import com.campx.student.repository.InMemoryAuditLogRepository;
import com.campx.student.model.ProfileChangeHistoryDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain Service governing institutional student identity and profile lifecycle,
 * field-level authorization, verification workflows, profile corrections,
 * alternate identifiers, preferences, and transactional outbox event publication (STM-01).
 */
public class StudentDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentDomainService.class);
    private static final String SERVICE_NAME = "student-service";
    private static final String AGGREGATE_TYPE = "STUDENT";
    private static final String SCHEMA_VERSION = "1.0.0";

    // Allowed MIME types for uploaded document references
    private static final Set<String> ALLOWED_DOC_MIMES = new HashSet<>(Arrays.asList(
            "application/pdf", "image/jpeg", "image/png", "image/webp"
    ));
    private static final long MAX_DOC_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-()]{7,25}$");

    private final StudentRepository studentRepository;
    private final PersonRepository personRepository;
    private final GuardianshipRepository guardianshipRepository;
    private final CorrectionRequestRepository correctionRequestRepository;
    private final StudentVerificationRepository verificationRepository;
    private final PersonIdentifierRepository identifierRepository;
    private final StudentDocumentRepository documentRepository;
    private final StudentAddressRepository addressRepository;
    private final StudentEventPublisher eventPublisher;
    private final AuditLogRepository auditRepository;
    private final ObjectMapper objectMapper;

    // In-memory idempotency store: key = tenantId + ":" + idempotencyKey, value = StudentProfileResponse
    private final Map<String, StudentProfileResponse> idempotencyStore = new HashMap<>();

    public static class RepositoryBundle {
        public final StudentRepository studentRepository;
        public final PersonRepository personRepository;
        public final GuardianshipRepository guardianshipRepository;
        public final CorrectionRequestRepository correctionRequestRepository;
        public final StudentVerificationRepository verificationRepository;
        public final PersonIdentifierRepository identifierRepository;
        public final StudentDocumentRepository documentRepository;
        public final StudentAddressRepository addressRepository;
        public final StudentEventPublisher eventPublisher;
        public final AuditLogRepository auditLogRepository;

        public RepositoryBundle(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                CorrectionRequestRepository correctionRequestRepository,
                                StudentVerificationRepository verificationRepository,
                                PersonIdentifierRepository identifierRepository,
                                StudentDocumentRepository documentRepository,
                                StudentAddressRepository addressRepository,
                                StudentEventPublisher eventPublisher) {
            this(studentRepository, personRepository, guardianshipRepository, correctionRequestRepository,
                    verificationRepository, identifierRepository, documentRepository, addressRepository,
                    eventPublisher, (studentRepository instanceof PostgresStudentRepository)
                            ? ((PostgresStudentRepository) studentRepository).getAuditRepository()
                            : new InMemoryAuditLogRepository());
        }

        public RepositoryBundle(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                CorrectionRequestRepository correctionRequestRepository,
                                StudentVerificationRepository verificationRepository,
                                PersonIdentifierRepository identifierRepository,
                                StudentDocumentRepository documentRepository,
                                StudentAddressRepository addressRepository,
                                StudentEventPublisher eventPublisher,
                                AuditLogRepository auditLogRepository) {
            this.studentRepository = studentRepository;
            this.personRepository = personRepository;
            this.guardianshipRepository = guardianshipRepository;
            this.correctionRequestRepository = correctionRequestRepository;
            this.verificationRepository = verificationRepository;
            this.identifierRepository = identifierRepository;
            this.documentRepository = documentRepository;
            this.addressRepository = addressRepository;
            this.eventPublisher = eventPublisher;
            this.auditLogRepository = auditLogRepository != null ? auditLogRepository : new InMemoryAuditLogRepository();
        }
    }

    public StudentDomainService() {
        this(resolveDefaultRepositories());
    }

    public StudentDomainService(RepositoryBundle bundle) {
        this(bundle.studentRepository, bundle.personRepository, bundle.guardianshipRepository,
                bundle.correctionRequestRepository, bundle.verificationRepository, bundle.identifierRepository,
                bundle.documentRepository, bundle.addressRepository, bundle.eventPublisher, bundle.auditLogRepository);
    }

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository) {
        this(studentRepository, personRepository, guardianshipRepository,
                new InMemoryCorrectionRequestRepository(),
                new InMemoryStudentVerificationRepository(),
                new InMemoryPersonIdentifierRepository(),
                new InMemoryStudentDocumentRepository(),
                new InMemoryStudentAddressRepository(),
                new StudentEventPublisher.InMemory());
    }

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                StudentEventPublisher eventPublisher) {
        this(studentRepository, personRepository, guardianshipRepository,
                new InMemoryCorrectionRequestRepository(),
                new InMemoryStudentVerificationRepository(),
                new InMemoryPersonIdentifierRepository(),
                new InMemoryStudentDocumentRepository(),
                new InMemoryStudentAddressRepository(),
                eventPublisher);
    }

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                CorrectionRequestRepository correctionRequestRepository,
                                StudentVerificationRepository verificationRepository,
                                PersonIdentifierRepository identifierRepository,
                                StudentDocumentRepository documentRepository,
                                StudentAddressRepository addressRepository,
                                StudentEventPublisher eventPublisher) {
        this(studentRepository, personRepository, guardianshipRepository,
                correctionRequestRepository, verificationRepository, identifierRepository,
                documentRepository, addressRepository, eventPublisher,
                (studentRepository instanceof PostgresStudentRepository)
                        ? ((PostgresStudentRepository) studentRepository).getAuditRepository()
                        : new InMemoryAuditLogRepository());
    }

    public StudentDomainService(StudentRepository studentRepository,
                                PersonRepository personRepository,
                                GuardianshipRepository guardianshipRepository,
                                CorrectionRequestRepository correctionRequestRepository,
                                StudentVerificationRepository verificationRepository,
                                PersonIdentifierRepository identifierRepository,
                                StudentDocumentRepository documentRepository,
                                StudentAddressRepository addressRepository,
                                StudentEventPublisher eventPublisher,
                                AuditLogRepository auditRepository) {
        if (studentRepository == null) throw new IllegalArgumentException("studentRepository cannot be null");
        if (personRepository == null) throw new IllegalArgumentException("personRepository cannot be null");
        if (guardianshipRepository == null) throw new IllegalArgumentException("guardianshipRepository cannot be null");

        if (isDeployedEnvironment()) {
            if (studentRepository instanceof InMemoryStudentRepository ||
                personRepository instanceof InMemoryPersonRepository ||
                (auditRepository instanceof InMemoryAuditLogRepository)) {
                throw new IllegalStateException("Ephemeral in-memory repositories cannot be used in a deployed environment.");
            }
        }
        this.studentRepository = studentRepository;
        this.personRepository = personRepository;
        this.guardianshipRepository = guardianshipRepository;
        this.correctionRequestRepository = correctionRequestRepository != null ? correctionRequestRepository : new InMemoryCorrectionRequestRepository();
        this.verificationRepository = verificationRepository != null ? verificationRepository : new InMemoryStudentVerificationRepository();
        this.identifierRepository = identifierRepository != null ? identifierRepository : new InMemoryPersonIdentifierRepository();
        this.documentRepository = documentRepository != null ? documentRepository : new InMemoryStudentDocumentRepository();
        this.addressRepository = addressRepository != null ? addressRepository : new InMemoryStudentAddressRepository();
        this.eventPublisher = eventPublisher != null ? eventPublisher : new StudentEventPublisher.InMemory();
        this.auditRepository = auditRepository != null ? auditRepository : new InMemoryAuditLogRepository();
        this.objectMapper = new ObjectMapper();
    }

    public StudentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public AuditLogRepository getAuditRepository() {
        return auditRepository;
    }

    public boolean isRelationalMode() {
        return studentRepository instanceof PostgresStudentRepository;
    }

    public static boolean isDeployedEnvironment() {
        String env = System.getProperty("campx.environment");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("CAMPX_ENVIRONMENT");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("DEPLOYMENT_ENV");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("APP_ENV");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("NODE_ENV");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("VERCEL_ENV");
        }
        return env != null && !env.trim().isEmpty()
                && !env.equalsIgnoreCase("test")
                && !env.equalsIgnoreCase("local")
                && !env.equalsIgnoreCase("development");
    }

    public static RepositoryBundle resolveDefaultRepositories() {
        String mode = System.getProperty("campx.persistence.mode");
        if (mode == null || mode.trim().isEmpty()) {
            mode = System.getenv("CAMPX_PERSISTENCE_MODE");
        }
        return resolveRepositories(mode, isDeployedEnvironment());
    }

    public static RepositoryBundle resolveRepositories(String mode, boolean isDeployed) {
        if (isDeployed) {
            if (mode == null || mode.trim().isEmpty()) {
                throw new IllegalStateException("Missing required persistence configuration in deployed environment. Deployed runtime must explicitly configure 'campx.persistence.mode=postgres'.");
            }
            String clean = mode.trim();
            if ("in_memory".equalsIgnoreCase(clean) || "inmemory".equalsIgnoreCase(clean) || "in-memory".equalsIgnoreCase(clean) || "mock".equalsIgnoreCase(clean)) {
                throw new IllegalStateException("Ephemeral in-memory persistence is strictly prohibited in deployed environment.");
            }
        }
        if (mode == null || mode.trim().isEmpty()) {
            return createInMemoryBundle();
        }
        String cleanMode = mode.trim();
        if ("in_memory".equalsIgnoreCase(cleanMode) || "inmemory".equalsIgnoreCase(cleanMode) || "in-memory".equalsIgnoreCase(cleanMode) || "mock".equalsIgnoreCase(cleanMode)) {
            return createInMemoryBundle();
        }
        if ("postgres".equalsIgnoreCase(cleanMode) || "relational".equalsIgnoreCase(cleanMode)) {
            DatabaseConnectionManager cm = DatabaseConnectionManager.getInstance();
            if (isDeployed) {
                cm.initialize();
            }
            AuditLogRepository auditRepo = new PostgresAuditRepository();
            return new RepositoryBundle(
                    new PostgresStudentRepository(cm),
                    new PostgresPersonRepository(cm),
                    new PostgresGuardianshipRepository(cm),
                    new PostgresCorrectionRequestRepository(cm),
                    new PostgresStudentVerificationRepository(cm),
                    new PostgresPersonIdentifierRepository(cm),
                    new PostgresStudentDocumentRepository(cm),
                    new PostgresStudentAddressRepository(cm),
                    new StudentEventPublisher.PostgresOutboxPublisher(cm),
                    auditRepo
            );
        }
        throw new IllegalStateException("Unsupported persistence mode: '" + mode + "'. Expected 'postgres', 'relational', or 'in_memory'.");
    }

    public static RepositoryBundle createInMemoryBundle() {
        return new RepositoryBundle(
                new InMemoryStudentRepository(),
                new InMemoryPersonRepository(),
                new InMemoryGuardianshipRepository(),
                new InMemoryCorrectionRequestRepository(),
                new InMemoryStudentVerificationRepository(),
                new InMemoryPersonIdentifierRepository(),
                new InMemoryStudentDocumentRepository(),
                new InMemoryStudentAddressRepository(),
                new StudentEventPublisher.InMemory(),
                new InMemoryAuditLogRepository()
        );
    }

    // =========================================================================
    // 1. Enrollment & Institutional Student Number (STM01-US-001, US-005, US-006, US-007, US-034, US-035)
    // =========================================================================

    public StudentProfileResponse enrollStudent(UserSecurityContext context, EnrollStudentRequest request) {
        return enrollStudent(context, request, null);
    }

    public StudentProfileResponse enrollStudent(UserSecurityContext context, EnrollStudentRequest request, String idempotencyKey) {
        assertTenantContext(context);
        validateEnrollmentRequest(request);

        // Check idempotency replay if key provided (STM01-US-035)
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String idempKey = context.getTenantId() + ":" + idempotencyKey.trim();
            synchronized (idempotencyStore) {
                if (idempotencyStore.containsKey(idempKey)) {
                    logger.info("Idempotent create replayed for key: {}", idempotencyKey);
                    return idempotencyStore.get(idempKey);
                }
            }
        }

        // 1. Determine & assign institutional student number (STM01-US-006)
        String enrollmentNo = request.getEnrollmentNo();
        if (enrollmentNo == null || enrollmentNo.trim().isEmpty()) {
            long seq = studentRepository.countByTenant(context) + 1;
            enrollmentNo = String.format("STU%d%05d", LocalDate.now().getYear(), seq);
        } else {
            enrollmentNo = enrollmentNo.trim();
        }

        // 2. Check duplicate enrollment_no in tenant (STM01-US-007)
        if (studentRepository.existsByEnrollmentNo(context, enrollmentNo)) {
            throw new StudentConflictException(String.format(
                    "Student with enrollment number '%s' already exists in tenant.", enrollmentNo));
        }

        // 3. Check duplicate primary_email in tenant
        if (personRepository.existsByEmail(context, request.getPrimaryEmail())) {
            throw new StudentConflictException(String.format(
                    "Person with email '%s' already exists in tenant.", request.getPrimaryEmail()));
        }

        // 4. Create core.persons record (Unicode and name length validated, STM01-US-005)
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

        // 5. Create stu.students record atomically (STM01-US-034)
        StudentEntity student = new StudentEntity();
        student.setPersonId(person.getId());
        student.setCollegeId(request.getCollegeId());
        student.setProgramId(request.getProgramId());
        student.setEnrollmentNo(enrollmentNo);
        student.setAdmissionNo(request.getAdmissionNo() != null ? request.getAdmissionNo().trim() : null);
        student.setAdmissionDate(request.getParsedAdmissionDate());
        student.setAdmissionAcademicYearId(request.getAdmissionAcademicYearId());
        student.setStatus(StudentStatus.ACTIVE);
        student.setVerificationStatus("UNVERIFIED");
        student = studentRepository.save(context, student);

        // 6. Optional primary guardian creation (STM01-US-018)
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

        // 7. Decoupled contract for Academics domain (ACD-04) if batch requested
        if (request.getBatchId() != null) {
            BatchAssignmentContract batchContract = new BatchAssignmentContract(
                    context.getTenantId(),
                    request.getCollegeId(),
                    student.getId(),
                    request.getBatchId(),
                    request.getRollNo(),
                    "ASSIGN"
            );
            logger.info("Emitted BatchAssignmentContract for Academics domain: {}", batchContract);
        }

        // 8. Publish transactional outbox event: student.enrolled.v1 (STM01-US-001, US-027)
        publishOutboxEvent(context, student.getId(), "student.enrolled.v1", String.format(
                "{\"studentId\":\"%s\",\"personId\":\"%s\",\"enrollmentNo\":\"%s\",\"programId\":\"%s\",\"collegeId\":\"%s\",\"status\":\"%s\"}",
                student.getId(), person.getId(), student.getEnrollmentNo(),
                student.getProgramId() != null ? student.getProgramId() : "",
                student.getCollegeId(), student.getStatus()
        ));

        logger.info("Enrolled student [{}] with enrollment_no [{}] in tenant [{}]",
                student.getId(), student.getEnrollmentNo(), context.getTenantId());

        recordPrivilegedAuditAction(context, "STUDENT_ENROLLED", student.getId(),
                Arrays.asList("enrollmentNo", "firstName", "lastName", "primaryEmail"),
                "Student enrolled with enrollment_no: " + student.getEnrollmentNo());

        StudentProfileResponse response = buildProfileResponse(context, student, person, guardianSummaries);

        // Store for idempotency if key provided
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            synchronized (idempotencyStore) {
                idempotencyStore.put(context.getTenantId() + ":" + idempotencyKey.trim(), response);
            }
        }

        return response;
    }

    // =========================================================================
    // 2. Profile Retrieval & Self-Service Authorization (STM01-US-002, US-003, US-030, US-031, US-032)
    // =========================================================================

    public StudentProfileResponse getMyProfile(UserSecurityContext context) {
        assertTenantContext(context);
        UUID userId = context.getUserId();

        // 1. Check if user is linked via iamUserId
        Optional<StudentEntity> optStudent = studentRepository.findByIamUserId(context, userId);

        // 2. Fall back to personId = userId
        if (!optStudent.isPresent()) {
            optStudent = studentRepository.findByPersonId(context, userId);
        }

        // 3. Fall back to person by email if available in profile
        if (!optStudent.isPresent()) {
            StudentEntity student = studentRepository.findById(context, userId).orElse(null);
            if (student != null) {
                optStudent = Optional.of(student);
            }
        }

        StudentEntity student = optStudent.orElseThrow(() ->
                new StudentNotFoundException("No student profile found mapped to authenticated user: " + userId));

        return getStudentById(context, student.getId());
    }

    public StudentProfileResponse getStudentById(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        // Enforce Tenant Isolation (STM01-US-030)
        if (!context.getTenantId().equals(student.getTenantId())) {
            throw new StudentNotFoundException("Student not found with ID: " + studentId);
        }

        // Enforce RBAC & Self-Service Restrictions (STM01-US-003, STM01-US-031)
        enforceReadAccess(context, student);

        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);
        List<GuardianshipEntity> guardians = guardianshipRepository.findByStudentId(context, student.getId());
        List<StudentProfileResponse.GuardianSummary> summaries = mapGuardianSummaries(context, guardians);

        return buildProfileResponse(context, student, person, summaries);
    }

    public CursorPagedResult<StudentRosterItem> getRoster(UserSecurityContext context, StudentRosterQuery query) {
        assertTenantContext(context);
        // Student role is not permitted to browse arbitrary roster (STM01-US-002, STM01-US-031)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students are not authorized to view institutional student rosters.");
        }
        return studentRepository.findRoster(context, query);
    }

    // =========================================================================
    // 3. Partial Profile Updates & Concurrency Control (STM01-US-004, US-005, US-033, US-036)
    // =========================================================================

    public StudentProfileResponse updatePermittedFields(UserSecurityContext context, UUID studentId, UpdateStudentProfileRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null) throw new StudentValidationException("Update request body cannot be null");

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        // Enforce update permissions
        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only modify their own profile.");
        }

        // Protected fields cannot be changed via generic patch (STM01-US-004)
        if (request.attemptsProtectedFieldUpdate()) {
            throw new StudentValidationException(
                    "Protected fields (legal name, date of birth, enrollment number, status) cannot be changed via generic patch; submit a correction request instead.");
        }

        // Optimistic concurrency control (STM01-US-033)
        if (request.getRowVersion() != null && request.getRowVersion() != student.getRowVersion()) {
            throw new StudentConflictException(String.format(
                    "Concurrency conflict: requested row version [%d] does not match current version [%d]. Refresh and retry.",
                    request.getRowVersion(), student.getRowVersion()));
        }

        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);
        List<String> changedFields = new ArrayList<>();

        if (person != null) {
            // Validate and update preferredName (Unicode safe, bounded length, STM01-US-005)
            if (request.getPreferredName() != null) {
                String pref = request.getPreferredName().trim();
                if (pref.length() > 100) {
                    throw new StudentValidationException("Preferred name cannot exceed 100 characters");
                }
                person.setPreferredName(pref);
                person.setDisplayName(pref);
                changedFields.add("preferredName");
            }

            // Primary phone syntax validation (STM01-US-016)
            if (request.getPrimaryPhone() != null) {
                String phone = request.getPrimaryPhone().trim();
                if (!PHONE_PATTERN.matcher(phone).matches()) {
                    throw new StudentValidationException("Invalid phone format: " + phone);
                }
                person.setPrimaryPhone(phone);
                changedFields.add("primaryPhone");
            }

            if (request.getBloodGroup() != null) {
                person.setBloodGroup(request.getBloodGroup().trim().toUpperCase());
                changedFields.add("bloodGroup");
            }

            if (request.getNationality() != null) {
                person.setNationality(request.getNationality().trim());
                changedFields.add("nationality");
            }

            if (request.getMaritalStatus() != null) {
                person.setMaritalStatus(request.getMaritalStatus().trim());
                changedFields.add("maritalStatus");
            }

            if (request.getPhotoDocumentId() != null) {
                person.setPhotoDocumentId(request.getPhotoDocumentId());
                changedFields.add("photoDocumentId");
            }

            person = personRepository.save(context, person);
        }

        // Update preferences if provided (STM01-US-022)
        if (request.getLocale() != null || request.getTimezone() != null || request.getCommunicationPreferences() != null) {
            try {
                Map<String, Object> prefMap = new HashMap<>();
                if (request.getLocale() != null) prefMap.put("locale", request.getLocale().trim());
                if (request.getTimezone() != null) prefMap.put("timezone", request.getTimezone().trim());
                if (request.getCommunicationPreferences() != null) prefMap.put("channels", request.getCommunicationPreferences());
                student.setPreferences(objectMapper.writeValueAsString(prefMap));
                changedFields.add("preferences");
            } catch (Exception e) {
                logger.warn("Failed to serialize preferences: {}", e.getMessage());
            }
        }

        // Save updated student (repository increments rowVersion)
        student = studentRepository.save(context, student);

        // Publish safe profile-updated event (changed fields only, no sensitive values, STM01-US-028, STM01-US-037)
        publishOutboxEvent(context, student.getId(), "student.profile.updated.v1", String.format(
                "{\"studentId\":\"%s\",\"version\":%d,\"changedFields\":%s,\"occurredAt\":\"%s\"}",
                student.getId(), student.getRowVersion(),
                toJsonArray(changedFields), OffsetDateTime.now()
        ));

        logger.info("Updated permitted fields for student [{}] in tenant [{}]", studentId, context.getTenantId());
        recordPrivilegedAuditAction(context, "STUDENT_UPDATED", student.getId(),
                changedFields, "Permitted fields updated: " + String.join(", ", changedFields));
        return getStudentById(context, studentId);
    }

    // =========================================================================
    // 4. Alternate Identifiers & Masking (STM01-US-008, US-009)
    // =========================================================================

    public PersonIdentifierDto addIdentifier(UserSecurityContext context, UUID studentId, PersonIdentifierDto dto) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (dto == null || dto.getIdType() == null || dto.getIdentifierValue() == null) {
            throw new StudentValidationException("Identifier type and value are required");
        }

        // Only authorized staff can add or manage alternate identifiers (STM01-US-008)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot self-assign alternate identifiers.");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        String raw = dto.getIdentifierValue().trim();
        String idType = dto.getIdType().trim().toUpperCase();

        if (identifierRepository.findByPersonIdAndType(context, student.getPersonId(), idType).isPresent()) {
            throw new StudentConflictException(String.format("Identifier of type [%s] already exists for student", idType));
        }

        PersonIdentifierEntity entity = new PersonIdentifierEntity();
        entity.setPersonId(student.getPersonId());
        entity.setIdType(idType);
        entity.setRawValue(raw);
        entity.setMaskedValue(PersonIdentifierDto.computeMaskedValue(raw));
        entity.setIssuingAuthority(dto.getIssuingAuthority());
        entity.setVerified(dto.isVerified());

        if (dto.getValidFrom() != null) {
            try { entity.setValidFrom(LocalDate.parse(dto.getValidFrom())); } catch (Exception ignored) {}
        }
        if (dto.getValidTo() != null) {
            try { entity.setValidTo(LocalDate.parse(dto.getValidTo())); } catch (Exception ignored) {}
        }

        entity = identifierRepository.save(context, entity);

        // Publish identifier assigned event
        publishOutboxEvent(context, student.getId(), "student.identifier.assigned.v1", String.format(
                "{\"studentId\":\"%s\",\"idType\":\"%s\",\"maskedValue\":\"%s\"}",
                student.getId(), entity.getIdType(), entity.getMaskedValue()
        ));

        recordPrivilegedAuditAction(context, "IDENTIFIER_ADDED", student.getId(),
                Collections.singletonList("identifier"), "Identifier added: " + entity.getIdType());

        PersonIdentifierDto result = toIdentifierDto(entity);
        result.setIdentifierValue(raw); // Full value for authorized creator
        return result;
    }

    public List<PersonIdentifierDto> getIdentifiers(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        enforceReadAccess(context, student);

        boolean fullAccess = hasPrivilegedStaffAccess(context);
        return identifierRepository.findByPersonId(context, student.getPersonId()).stream()
                .map(e -> {
                    PersonIdentifierDto dto = toIdentifierDto(e);
                    if (!fullAccess) {
                        dto.setIdentifierValue(e.getMaskedValue()); // Mask for student or general viewers (STM01-US-009)
                    } else {
                        dto.setIdentifierValue(e.getRawValue());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 5. Profile Correction Workflow (STM01-US-010, US-011, US-012)
    // =========================================================================

    public StudentCorrectionRequestEntity submitCorrectionRequest(UserSecurityContext context, UUID studentId, SubmitCorrectionRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getTargetField() == null || request.getProposedValue() == null) {
            throw new StudentValidationException("Target field and proposed value are required for correction request");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new StudentValidationException("Correction request reason is required");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only submit correction requests for their own profile.");
        }

        String field = request.getTargetField().trim();
        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);
        String oldValue = resolveCurrentFieldValue(student, person, field);

        StudentCorrectionRequestEntity entity = new StudentCorrectionRequestEntity();
        entity.setStudentId(student.getId());
        entity.setPersonId(student.getPersonId());
        entity.setRequestedBy(context.getUserId());
        entity.setTargetField(field);
        entity.setOldValue(oldValue);
        entity.setNewValue(request.getProposedValue().trim());
        entity.setReason(request.getReason() != null ? request.getReason().trim() : "Profile correction requested");
        entity.setEvidenceDocumentId(request.getEvidenceDocumentId());
        entity.setStatus("PENDING");

        entity = correctionRequestRepository.save(context, entity);

        // Publish event (STM01-US-010)
        publishOutboxEvent(context, student.getId(), "student.profile.correction.requested.v1", String.format(
                "{\"studentId\":\"%s\",\"requestId\":\"%s\",\"targetField\":\"%s\",\"status\":\"PENDING\"}",
                student.getId(), entity.getId(), entity.getTargetField()
        ));

        logger.info("Submitted correction request [{}] for student [{}]", entity.getId(), studentId);
        return entity;
    }

    public List<StudentCorrectionRequestEntity> getCorrectionRequests(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        enforceReadAccess(context, student);
        return correctionRequestRepository.findByStudentId(context, studentId);
    }

    public StudentCorrectionRequestEntity decideCorrectionRequest(UserSecurityContext context, UUID studentId, UUID requestId, DecideCorrectionRequest decision) {
        assertTenantContext(context);
        if (requestId == null) throw new StudentValidationException("requestId cannot be null");
        if (decision == null || decision.getDecision() == null) {
            throw new StudentValidationException("Decision (APPROVED/REJECTED) is required");
        }

        // Only authorized staff/reviewers can decide correction requests (STM01-US-011)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot decide profile correction requests.");
        }

        StudentCorrectionRequestEntity correction = correctionRequestRepository.findById(context, requestId)
                .orElseThrow(() -> new StudentNotFoundException("Correction request not found with ID: " + requestId));

        // Segregation of Duties: requester cannot decide their own correction (STM01-US-011)
        if (correction.getRequestedBy() != null && correction.getRequestedBy().equals(context.getUserId())) {
            throw new StudentSecurityViolationException("Segregation of duties violation: requester cannot approve their own correction request.");
        }

        if (!"PENDING".equalsIgnoreCase(correction.getStatus())) {
            throw new StudentConflictException("Correction request is already in state: " + correction.getStatus());
        }

        boolean approved = decision.isApproved();
        correction.setStatus(approved ? "APPROVED" : "REJECTED");
        correction.setDecidedBy(context.getUserId());
        correction.setDecisionReason(decision.getReason() != null ? decision.getReason().trim() : null);
        correction.setDecidedAt(OffsetDateTime.now());

        // If approved, apply the requested change to canonical record
        if (approved) {
            applyApprovedCorrection(context, correction);
        }

        correction = correctionRequestRepository.save(context, correction);

        // Publish event
        publishOutboxEvent(context, studentId, "student.profile.correction.decided.v1", String.format(
                "{\"studentId\":\"%s\",\"requestId\":\"%s\",\"status\":\"%s\",\"decidedBy\":\"%s\"}",
                studentId, correction.getId(), correction.getStatus(), context.getUserId()
        ));

        logger.info("Decided correction request [{}] for student [{}]: outcome={}", requestId, studentId, correction.getStatus());
        recordPrivilegedAuditAction(context, "CORRECTION_DECIDED", studentId,
                Collections.singletonList(correction.getTargetField()), "Correction request decided: " + decision.getDecision());
        return correction;
    }

    // =========================================================================
    // 6. Profile Verification Workflow (STM01-US-013, US-014, US-047)
    // =========================================================================

    public StudentVerificationEntity initiateVerification(UserSecurityContext context, UUID studentId, InitiateVerificationRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");

        // Verifier role required (STM01-US-013)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot initiate profile verification.");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        StudentVerificationEntity verification = new StudentVerificationEntity();
        verification.setStudentId(student.getId());
        verification.setVerificationType(request != null && request.getVerificationType() != null ?
                request.getVerificationType().trim().toUpperCase() : "IDENTITY");
        verification.setEvidenceReference(request != null ? request.getEvidenceReference() : null);
        verification.setStatus("PENDING");

        student.setVerificationStatus("PENDING_VERIFICATION");
        studentRepository.save(context, student);

        verification = verificationRepository.save(context, verification);

        publishOutboxEvent(context, student.getId(), "student.verification.initiated.v1", String.format(
                "{\"studentId\":\"%s\",\"verificationId\":\"%s\",\"status\":\"PENDING\"}",
                student.getId(), verification.getId()
        ));

        return verification;
    }

    public StudentVerificationEntity decideVerification(UserSecurityContext context, UUID studentId, UUID verificationId, DecideVerificationRequest decision) {
        assertTenantContext(context);
        if (verificationId == null) throw new StudentValidationException("verificationId cannot be null");
        if (decision == null || decision.getStatus() == null) {
            throw new StudentValidationException("Verification decision status is required");
        }

        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot decide verification.");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        StudentVerificationEntity verification = verificationRepository.findById(context, verificationId)
                .orElseThrow(() -> new StudentNotFoundException("Verification record not found with ID: " + verificationId));

        String targetStatus = decision.getStatus().trim().toUpperCase();
        if (!Arrays.asList("VERIFIED", "REJECTED", "CORRECTION_REQUIRED").contains(targetStatus)) {
            throw new StudentValidationException("Invalid verification status: " + targetStatus);
        }

        verification.setStatus(targetStatus);
        verification.setVerifiedBy(context.getUserId());
        verification.setVerifiedAt(OffsetDateTime.now());
        verification.setReason(decision.getReason());
        verification.setValidTo(decision.getParsedValidTo());

        // Update student verification status
        student.setVerificationStatus(targetStatus);
        student.setVerificationExpiry(decision.getParsedValidTo());
        studentRepository.save(context, student);

        verification = verificationRepository.save(context, verification);

        publishOutboxEvent(context, student.getId(), "student.profile.verification.changed.v1", String.format(
                "{\"studentId\":\"%s\",\"status\":\"%s\",\"verifiedBy\":\"%s\"}",
                student.getId(), targetStatus, context.getUserId()
        ));

        logger.info("Verification decided for student [{}]: {}", studentId, targetStatus);
        recordPrivilegedAuditAction(context, "VERIFICATION_DECIDED", studentId,
                Collections.singletonList("verificationStatus"), "Verification decided: " + targetStatus);
        return verification;
    }

    // =========================================================================
    // 7. Lifecycle State Machine (STM01-US-015, US-039)
    // =========================================================================

    public StudentProfileResponse transitionLifecycle(UserSecurityContext context, UUID studentId, UpdateStudentStatusRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getStatus() == null) {
            throw new StudentValidationException("Target student status is required");
        }

        // Only authorized staff can alter lifecycle state (STM01-US-015)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot alter their own lifecycle state.");
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
        student.setRowVersion(student.getRowVersion() + 1);
        student = studentRepository.save(context, student);

        publishOutboxEvent(context, student.getId(), "student.status_changed.v1", String.format(
                "{\"studentId\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"reason\":\"%s\"}",
                student.getId(), current, target,
                request.getReason() != null ? request.getReason().replace("\"", "\\\"") : ""
        ));

        logger.info("Transitioned student [{}] from [{}] to [{}]", studentId, current, target);
        recordPrivilegedAuditAction(context, "STATUS_CHANGED", student.getId(),
                Collections.singletonList("status"), "Status transitioned from " + current + " to " + target);
        return getStudentById(context, studentId);
    }

    // =========================================================================
    // 8. Contact Details & Address Management (STM01-US-016, US-017)
    // =========================================================================

    public StudentAddressEntity updateAddress(UserSecurityContext context, UUID studentId, StudentAddressRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getLine1() == null || request.getPostalCode() == null) {
            throw new StudentValidationException("Address line1 and postal code are required");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only modify their own address.");
        }

        PersonEntity person = personRepository.findById(context, student.getPersonId())
                .orElseThrow(() -> new StudentNotFoundException("Person record not found for student"));

        StudentAddressEntity address = new StudentAddressEntity();
        address.setLine1(request.getLine1().trim());
        address.setLine2(request.getLine2() != null ? request.getLine2().trim() : null);
        address.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);
        address.setCity(request.getCity() != null ? request.getCity().trim() : null);
        address.setDistrict(request.getDistrict() != null ? request.getDistrict().trim() : null);
        address.setStateCode(request.getStateCode() != null ? request.getStateCode().trim() : null);
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountryCode(request.getCountryCode() != null ? request.getCountryCode().trim() : "IND");

        address = addressRepository.save(context, address);

        boolean isPermanent = "PERMANENT".equalsIgnoreCase(request.getAddressType());
        if (isPermanent) {
            person.setPermanentAddressId(address.getId());
        } else {
            person.setCurrentAddressId(address.getId());
        }
        personRepository.save(context, person);

        logger.info("Updated [{}] address for student [{}]", request.getAddressType(), studentId);
        return address;
    }

    // =========================================================================
    // 9. Guardians, Consent & Visibility (STM01-US-018, US-019, US-048)
    // =========================================================================

    public StudentProfileResponse addGuardian(UserSecurityContext context, UUID studentId, EnrollStudentRequest.GuardianRequest gReq) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot add or link guardians directly; staff authorization required.");
        }
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

        // Audit guardian addition (STM01-US-048)
        publishOutboxEvent(context, student.getId(), "student.guardian.linked.v1", String.format(
                "{\"studentId\":\"%s\",\"guardianPersonId\":\"%s\",\"relationship\":\"%s\"}",
                student.getId(), guardianPerson.getId(), guardianship.getRelationship()
        ));

        return getStudentById(context, studentId);
    }

    public StudentProfileResponse updateGuardianConsent(UserSecurityContext context, UUID studentId, UUID guardianshipId,
                                                        boolean canViewAcademics, boolean canViewFees, boolean canPayFees) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only modify consent for their own guardians.");
        }

        List<GuardianshipEntity> guardians = guardianshipRepository.findByStudentId(context, studentId);
        GuardianshipEntity target = guardians.stream()
                .filter(g -> g.getId().equals(guardianshipId))
                .findFirst()
                .orElseThrow(() -> new StudentNotFoundException("Guardianship not found with ID: " + guardianshipId));

        target.setCanViewAcademics(canViewAcademics);
        target.setCanViewFees(canViewFees);
        target.setCanPayFees(canPayFees);
        guardianshipRepository.save(context, target);

        // Audit consent update (STM01-US-048)
        publishOutboxEvent(context, student.getId(), "student.guardian.consent_updated.v1", String.format(
                "{\"studentId\":\"%s\",\"guardianshipId\":\"%s\",\"canViewAcademics\":%b,\"canViewFees\":%b,\"canPayFees\":%b}",
                studentId, guardianshipId, canViewAcademics, canViewFees, canPayFees
        ));

        logger.info("Updated guardian consent for student [{}] guardianship [{}]", studentId, guardianshipId);
        return getStudentById(context, studentId);
    }

    // =========================================================================
    // 10. Document Metadata Attachment (STM01-US-020, US-021, US-042)
    // =========================================================================

    public StudentDocumentEntity attachDocument(UserSecurityContext context, UUID studentId, AttachDocumentRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getDocumentType() == null || request.getStoragePath() == null) {
            throw new StudentValidationException("Document type and storage path are required");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only attach documents to their own profile.");
        }

        // Validate MIME type & size (STM01-US-020)
        String mime = request.getMimeType() != null ? request.getMimeType().trim().toLowerCase() : "application/pdf";
        if (!ALLOWED_DOC_MIMES.contains(mime)) {
            throw new StudentValidationException("Disallowed document MIME type: " + mime + ". Allowed: PDF, JPEG, PNG, WEBP.");
        }
        if (request.getSizeBytes() > MAX_DOC_SIZE_BYTES) {
            throw new StudentValidationException("Document exceeds maximum allowed size of 10MB");
        }

        StudentDocumentEntity doc = new StudentDocumentEntity();
        doc.setStudentId(student.getId());
        doc.setTitle(request.getTitle() != null ? request.getTitle().trim() : request.getDocumentType());
        doc.setDocumentType(request.getDocumentType().trim().toUpperCase());
        doc.setStorageBucket(request.getStorageBucket() != null ? request.getStorageBucket().trim() : "student-docs");
        doc.setStoragePath(request.getStoragePath().trim());
        doc.setMimeType(mime);
        doc.setSizeBytes(request.getSizeBytes());
        doc.setClassification(request.getClassification() != null ? request.getClassification().trim() : "CONFIDENTIAL");
        doc.setStatus("UPLOADED");
        doc.setValidTo(request.getParsedValidTo());

        doc = documentRepository.save(context, doc);

        publishOutboxEvent(context, student.getId(), "student.document.attached.v1", String.format(
                "{\"studentId\":\"%s\",\"documentId\":\"%s\",\"documentType\":\"%s\"}",
                student.getId(), doc.getId(), doc.getDocumentType()
        ));

        logger.info("Attached document [{}] to student [{}]", doc.getId(), studentId);
        return doc;
    }

    public List<StudentDocumentEntity> getDocuments(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));
        enforceReadAccess(context, student);
        return documentRepository.findByStudentId(context, studentId);
    }

    // =========================================================================
    // 11. Preferences & IAM Linkage (STM01-US-022, US-023, US-024, US-045)
    // =========================================================================

    public StudentProfileResponse linkIamAccount(UserSecurityContext context, UUID studentId, IamLinkageRequest request) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        if (request == null || request.getIamUserId() == null) {
            throw new StudentValidationException("iamUserId is required for account linkage");
        }

        // Only authorized staff or provisioning workflow can link IAM (STM01-US-023)
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot self-assign IAM user identities.");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        student.setIamUserId(request.getIamUserId());
        student = studentRepository.save(context, student);

        publishOutboxEvent(context, student.getId(), "student.account.linked.v1", String.format(
                "{\"studentId\":\"%s\",\"iamUserId\":\"%s\"}",
                student.getId(), request.getIamUserId()
        ));

        logger.info("Linked student [{}] to IAM user [{}]", studentId, request.getIamUserId());
        recordPrivilegedAuditAction(context, "IAM_LINKED", student.getId(),
                Collections.singletonList("iamUserId"), "Linked to IAM account: " + request.getIamUserId());
        return getStudentById(context, studentId);
    }

    public void requestAccountProvisioning(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        publishOutboxEvent(context, student.getId(), "student.provisioning.requested.v1", String.format(
                "{\"studentId\":\"%s\",\"enrollmentNo\":\"%s\",\"tenantId\":\"%s\"}",
                student.getId(), student.getEnrollmentNo(), context.getTenantId()
        ));

        logger.info("Requested IAM provisioning for student [{}]", studentId);
    }

    // =========================================================================
    // 12. Downstream Projections & Intelligence Contract (STM01-US-044, US-049)
    // =========================================================================

    public StudentProjectionResponse getDownstreamProjection(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot access internal intelligence projections.");
        }
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        PersonEntity person = personRepository.findById(context, student.getPersonId()).orElse(null);

        StudentProjectionResponse resp = new StudentProjectionResponse();
        resp.setStudentId(student.getId());
        resp.setTenantId(student.getTenantId());
        resp.setCollegeId(student.getCollegeId());
        resp.setProgramId(student.getProgramId());
        resp.setEnrollmentNo(student.getEnrollmentNo());
        resp.setStatus(student.getStatus());
        resp.setVerificationStatus(student.getVerificationStatus());
        resp.setDisplayName(person != null ? person.getDisplayName() : student.getEnrollmentNo());
        return resp;
    }

    // =========================================================================
    // 13. Soft Deletion / Deactivation (STM01-US-039)
    // =========================================================================

    public void deactivateStudent(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");

        if (isStudentRole(context)) {
            throw new StudentSecurityViolationException("Students cannot deactivate records.");
        }

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        studentRepository.delete(context, studentId);
        logger.info("Deactivated student [{}] ({}) in tenant [{}]", studentId, student.getEnrollmentNo(), context.getTenantId());
        recordPrivilegedAuditAction(context, "STUDENT_DEACTIVATED", studentId,
                Collections.singletonList("status"), "Student deactivated (soft delete)");
    }

    public StudentProfileResponse getStudentProfile(UserSecurityContext context, UUID studentId) {
        return getStudentById(context, studentId);
    }

    public StudentProfileResponse updateStudentProfile(UserSecurityContext context, UUID studentId, UpdateStudentProfileRequest request, String ifMatch) {
        if (ifMatch != null && !ifMatch.trim().isEmpty()) {
            try {
                String clean = ifMatch.replace("\"", "").replace("W/", "").trim();
                request.setRowVersion(Integer.parseInt(clean));
            } catch (Exception ignored) {}
        }
        return updatePermittedFields(context, studentId, request);
    }

    public StudentProfileResponse linkIamAccount(UserSecurityContext context, UUID studentId, UUID iamUserId) {
        return linkIamAccount(context, studentId, new IamLinkageRequest(iamUserId));
    }

    public void requestAccountProvisioning(UserSecurityContext context, UUID studentId, IamLinkageRequest req) {
        requestAccountProvisioning(context, studentId);
    }

    public StudentProfileResponse linkGuardian(UserSecurityContext context, UUID studentId, EnrollStudentRequest.GuardianRequest gReq) {
        return addGuardian(context, studentId, gReq);
    }

    public StudentProfileResponse updateGuardian(UserSecurityContext context, UUID studentId, UUID guardianshipId, EnrollStudentRequest.GuardianRequest gReq) {
        return updateGuardianConsent(context, studentId, guardianshipId, gReq.isCanViewAcademics(), gReq.isCanViewFees(), gReq.isCanPayFees());
    }

    public List<StudentProfileResponse.GuardianSummary> getGuardians(UserSecurityContext context, UUID studentId) {
        return getStudentById(context, studentId).getGuardians();
    }

    public List<StudentVerificationEntity> getVerificationHistory(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));
        enforceReadAccess(context, student);
        return verificationRepository.findByStudentId(context, studentId);
    }

    public StudentVerificationEntity decideVerification(UserSecurityContext context, UUID verificationId, DecideVerificationRequest decision) {
        assertTenantContext(context);
        StudentVerificationEntity v = verificationRepository.findById(context, verificationId)
                .orElseThrow(() -> new StudentNotFoundException("Verification record not found with ID: " + verificationId));
        return decideVerification(context, v.getStudentId(), verificationId, decision);
    }

    public StudentCorrectionRequestEntity decideCorrectionRequest(UserSecurityContext context, UUID requestId, DecideCorrectionRequest decision) {
        assertTenantContext(context);
        StudentCorrectionRequestEntity c = correctionRequestRepository.findById(context, requestId)
                .orElseThrow(() -> new StudentNotFoundException("Correction request not found with ID: " + requestId));
        return decideCorrectionRequest(context, c.getStudentId(), requestId, decision);
    }

    public StudentProjectionResponse getStudentProjection(UserSecurityContext context, UUID studentId) {
        return getDownstreamProjection(context, studentId);
    }

    public StudentProfileResponse updatePreferences(UserSecurityContext context, UUID studentId, StudentPreferencesDto prefs) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");
        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only modify preferences for their own profile.");
        }

        try {
            student.setPreferences(objectMapper.writeValueAsString(prefs));
        } catch (Exception e) {
            logger.warn("Failed to serialize preferences", e);
        }
        student = studentRepository.save(context, student);

        publishOutboxEvent(context, student.getId(), "student.preferences.updated.v1", String.format(
                "{\"studentId\":\"%s\",\"locale\":\"%s\",\"timezone\":\"%s\"}",
                student.getId(), prefs != null ? prefs.getLocale() : "en-IN", prefs != null ? prefs.getTimezone() : "Asia/Kolkata"
        ));

        return getStudentById(context, studentId);
    }

    public StudentPreferencesDto getPreferences(UserSecurityContext context, UUID studentId) {
        StudentProfileResponse profile = getStudentById(context, studentId);
        return profile.getPreferences() != null ? profile.getPreferences() : new StudentPreferencesDto();
    }

    /**
     * Retrieves permission-scoped profile change history (STM01-US-025).
     * Students may view only their own history. Staff may view history within their tenant.
     * Reuses shared audit infrastructure (audit.change_log & correction requests).
     */
    public List<ProfileChangeHistoryDto> getProfileChangeHistory(UserSecurityContext context, UUID studentId) {
        assertTenantContext(context);
        if (studentId == null) throw new StudentValidationException("studentId cannot be null");

        StudentEntity student = studentRepository.findById(context, studentId)
                .orElseThrow(() -> new StudentNotFoundException("Student not found with ID: " + studentId));

        if (!context.getTenantId().equals(student.getTenantId())) {
            throw new StudentNotFoundException("Student not found with ID: " + studentId);
        }

        if (isStudentRole(context) && !isOwnProfile(context, student)) {
            throw new StudentSecurityViolationException("Students can only view change history for their own profile.");
        }

        List<ProfileChangeHistoryDto> result = new ArrayList<>();

        // 1. Fetch mutation change logs from audit repository
        if (auditRepository != null) {
            try {
                List<AuditChangeLog> logs;
                if (isRelationalMode()) {
                    try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
                        logs = auditRepository.findChangeLogsByRecord(conn, context.getTenantId(), "stu", "students", studentId);
                    }
                } else {
                    logs = auditRepository.findChangeLogsByRecord(null, context.getTenantId(), "stu", "students", studentId);
                }
                if (logs != null) {
                    for (AuditChangeLog log : logs) {
                        String actionName = "I".equalsIgnoreCase(log.getAction()) ? "STUDENT_ENROLLED"
                                : ("D".equalsIgnoreCase(log.getAction()) ? "STUDENT_DEACTIVATED" : "PROFILE_UPDATED");
                        result.add(new ProfileChangeHistoryDto(
                                log.getId(),
                                studentId,
                                actionName,
                                log.getChangedFields(),
                                log.getActorId(),
                                log.getCreatedAt(),
                                "Mutation recorded for stu.students fields: " + String.join(", ", log.getChangedFields())
                        ));
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to retrieve change logs for student {}: {}", studentId, e.getMessage());
            }
        }

        // 2. Fetch approved correction requests as historical change events
        List<StudentCorrectionRequestEntity> corrections = correctionRequestRepository.findByStudentId(context, studentId);
        if (corrections != null) {
            for (StudentCorrectionRequestEntity c : corrections) {
                if ("APPROVED".equalsIgnoreCase(c.getStatus())) {
                    result.add(new ProfileChangeHistoryDto(
                            c.getId(),
                            studentId,
                            "CORRECTION_APPLIED",
                            Collections.singletonList(c.getTargetField()),
                            c.getDecidedBy(),
                            c.getDecidedAt() != null ? c.getDecidedAt().toInstant() : Instant.now(),
                            "Protected field correction approved: " + c.getTargetField()
                    ));
                }
            }
        }

        // Sort descending by timestamp
        result.sort((a, b) -> {
            if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
            if (a.getTimestamp() == null) return 1;
            if (b.getTimestamp() == null) return -1;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });

        return result;
    }

    /**
     * Records an immutable, tamper-evident audit record in audit.events and audit.change_log
     * for high-value privileged profile operations (STM01-US-026).
     */
    private void recordPrivilegedAuditAction(UserSecurityContext context, String action, UUID studentId, List<String> changedFields, String summary) {
        if (auditRepository != null && context != null && context.getTenantId() != null) {
            try {
                String correlationId = UUID.randomUUID().toString();
                AuditRecord r = AuditRecord.builder()
                        .action(action)
                        .tenantId(context.getTenantId())
                        .principalId(context.getUserId())
                        .principalType("USER")
                        .resourceType("STUDENT")
                        .resourceId(studentId)
                        .status("SUCCESS")
                        .severity("INFO")
                        .correlationId(correlationId)
                        .detailJson(String.format("{\"summary\":\"%s\"}", summary != null ? summary.replace("\"", "\\\"") : ""))
                        .build();

                AuditChangeLog c = AuditChangeLog.builder()
                        .tenantId(context.getTenantId())
                        .tableSchema("stu")
                        .tableName("students")
                        .recordId(studentId)
                        .action(action.contains("ENROLL") ? "I" : (action.contains("DEACTIVAT") ? "D" : "U"))
                        .changedFields(changedFields != null ? changedFields : Collections.emptyList())
                        .actorId(context.getUserId())
                        .requestId(correlationId)
                        .build();

                if (isRelationalMode()) {
                    context.executeInTransaction(DatabaseConnectionManager.getInstance(), conn -> {
                        auditRepository.appendAuditEvent(conn, r);
                        auditRepository.appendChangeLog(conn, c);
                        return null;
                    });
                } else {
                    auditRepository.appendAuditEvent(null, r);
                    auditRepository.appendChangeLog(null, c);
                }
            } catch (Exception e) {
                logger.warn("Failed to append audit record for action {}: {}", action, e.getMessage());
                if (isDeployedEnvironment()) {
                    throw new IllegalStateException("Audit record persistence failed in deployed environment: " + e.getMessage(), e);
                }
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void assertTenantContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new StudentSecurityViolationException("Missing required authenticated tenant context");
        }
    }

    private boolean isStudentRole(UserSecurityContext context) {
        String role = context.getRole();
        return role != null && (role.equalsIgnoreCase("student") || role.equalsIgnoreCase("r25"));
    }

    private boolean hasPrivilegedStaffAccess(UserSecurityContext context) {
        String role = context.getRole();
        if (role == null) return false;
        String r = role.toLowerCase();
        return r.contains("admin") || r.contains("registrar") || r.contains("admissions") || r.equals("r01") || r.equals("r04");
    }

    private boolean isOwnProfile(UserSecurityContext context, StudentEntity student) {
        UUID uid = context.getUserId();
        if (uid == null) return false;
        return uid.equals(student.getPersonId())
                || uid.equals(student.getIamUserId())
                || uid.equals(student.getId());
    }

    private void enforceReadAccess(UserSecurityContext context, StudentEntity student) {
        if (isStudentRole(context)) {
            if (!isOwnProfile(context, student)) {
                throw new StudentSecurityViolationException("Students are strictly restricted to viewing their own profile.");
            }
        }
        String role = context.getRole() != null ? context.getRole().toLowerCase() : "";
        if (role.contains("guardian") || role.contains("parent") || role.equals("r26")) {
            // Verify guardian link and consent
            List<GuardianshipEntity> guardians = guardianshipRepository.findByStudentId(context, student.getId());
            boolean linked = guardians.stream().anyMatch(g ->
                    context.getUserId().equals(g.getGuardianPersonId()) && g.isCanViewAcademics()
            );
            if (!linked) {
                throw new StudentSecurityViolationException("Guardian has no verified or consented relationship with this student.");
            }
        }
    }

    private void applyApprovedCorrection(UserSecurityContext context, StudentCorrectionRequestEntity correction) {
        String field = correction.getTargetField();
        String newVal = correction.getNewValue();

        PersonEntity person = personRepository.findById(context, correction.getPersonId()).orElse(null);
        if (person != null) {
            if ("legalName".equalsIgnoreCase(field) || "name".equalsIgnoreCase(field)) {
                String[] parts = newVal.trim().split("\\s+", 2);
                person.setFirstName(parts[0]);
                if (parts.length > 1) person.setLastName(parts[1]);
                person.setDisplayName(newVal.trim());
            } else if ("firstName".equalsIgnoreCase(field)) {
                person.setFirstName(newVal.trim());
            } else if ("lastName".equalsIgnoreCase(field)) {
                person.setLastName(newVal.trim());
            } else if ("dateOfBirth".equalsIgnoreCase(field)) {
                try { person.setDateOfBirth(LocalDate.parse(newVal.trim())); } catch (Exception ignored) {}
            } else if ("gender".equalsIgnoreCase(field)) {
                try { person.setGender(Gender.valueOf(newVal.trim().toUpperCase())); } catch (Exception ignored) {}
            } else if ("primaryEmail".equalsIgnoreCase(field)) {
                person.setPrimaryEmail(newVal.trim().toLowerCase());
            }
            personRepository.save(context, person);
        }
    }

    private String resolveCurrentFieldValue(StudentEntity student, PersonEntity person, String field) {
        if (person != null) {
            if ("legalName".equalsIgnoreCase(field)) return person.getDisplayName();
            if ("firstName".equalsIgnoreCase(field)) return person.getFirstName();
            if ("lastName".equalsIgnoreCase(field)) return person.getLastName();
            if ("dateOfBirth".equalsIgnoreCase(field)) return person.getDateOfBirth() != null ? person.getDateOfBirth().toString() : "";
            if ("gender".equalsIgnoreCase(field)) return person.getGender() != null ? person.getGender().name() : "";
            if ("primaryEmail".equalsIgnoreCase(field)) return person.getPrimaryEmail();
        }
        if (student != null) {
            if ("enrollmentNo".equalsIgnoreCase(field)) return student.getEnrollmentNo();
            if ("admissionNo".equalsIgnoreCase(field)) return student.getAdmissionNo();
        }
        return "";
    }

    private void validateEnrollmentRequest(EnrollStudentRequest req) {
        if (req == null) throw new StudentValidationException("EnrollStudentRequest cannot be null");
        if (req.getFirstName() == null || req.getFirstName().trim().isEmpty()) {
            throw new StudentValidationException("Student first name is required");
        }
        if (req.getFirstName().trim().length() > 100) {
            throw new StudentValidationException("First name cannot exceed 100 characters");
        }
        if (req.getLastName() != null && req.getLastName().trim().length() > 100) {
            throw new StudentValidationException("Last name cannot exceed 100 characters");
        }
        if (req.getPrimaryEmail() == null || req.getPrimaryEmail().trim().isEmpty() || !req.getPrimaryEmail().contains("@")) {
            throw new StudentValidationException("Valid student primary email is required");
        }
        if (req.getEnrollmentNo() != null && !req.getEnrollmentNo().trim().isEmpty() && req.getEnrollmentNo().trim().length() < 3) {
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

    private StudentProfileResponse buildProfileResponse(UserSecurityContext context, StudentEntity student, PersonEntity person,
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
        resp.setVerificationStatus(student.getVerificationStatus());
        if (student.getVerificationExpiry() != null) {
            resp.setVerificationExpiry(student.getVerificationExpiry().toString());
        }
        resp.setIamUserId(student.getIamUserId());
        resp.setRowVersion(student.getRowVersion());
        resp.setCreatedAt(student.getCreatedAt());
        resp.setUpdatedAt(student.getUpdatedAt());

        boolean fullStaffAccess = hasPrivilegedStaffAccess(context);

        if (person != null) {
            resp.setFirstName(person.getFirstName());
            resp.setMiddleName(person.getMiddleName());
            resp.setLastName(person.getLastName());
            resp.setPreferredName(person.getPreferredName());
            resp.setDisplayName(person.getDisplayName());
            resp.setPrimaryEmail(person.getPrimaryEmail());
            resp.setPrimaryPhone(person.getPrimaryPhone());
            resp.setGender(person.getGender());
            resp.setBloodGroup(person.getBloodGroup());
            resp.setNationality(person.getNationality());
            resp.setMaritalStatus(person.getMaritalStatus());
            resp.setPhotoDocumentId(person.getPhotoDocumentId());

            // Mask or omit DOB if caller is not authorized for sensitive PII (STM01-US-009, US-037)
            if (fullStaffAccess || isStudentRole(context)) {
                resp.setDateOfBirth(person.getDateOfBirth());
            }

            // Addresses
            if (person.getPermanentAddressId() != null) {
                addressRepository.findById(context, person.getPermanentAddressId()).ifPresent(resp::setPermanentAddress);
            }
            if (person.getCurrentAddressId() != null) {
                addressRepository.findById(context, person.getCurrentAddressId()).ifPresent(resp::setCurrentAddress);
            }

            // Identifiers with field-level masking
            List<PersonIdentifierDto> idDtos = identifierRepository.findByPersonId(context, person.getId()).stream()
                    .map(e -> {
                        PersonIdentifierDto dto = toIdentifierDto(e);
                        if (!fullStaffAccess) {
                            dto.setIdentifierValue(null);
                        } else {
                            dto.setIdentifierValue(e.getRawValue());
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());
            resp.setIdentifiers(idDtos);
        }

        if (guardians != null) {
            resp.setGuardians(guardians);
        }

        // Attached documents
        List<StudentDocumentEntity> docs = documentRepository.findByStudentId(context, student.getId());
        resp.setDocuments(docs);

        // Deserialized preferences
        if (student.getPreferences() != null && !student.getPreferences().trim().isEmpty()) {
            try {
                StudentPreferencesDto pDto = objectMapper.readValue(student.getPreferences(), StudentPreferencesDto.class);
                resp.setPreferences(pDto);
            } catch (Exception ignored) {}
        }

        return resp;
    }

    private PersonIdentifierDto toIdentifierDto(PersonIdentifierEntity e) {
        PersonIdentifierDto dto = new PersonIdentifierDto();
        dto.setId(e.getId());
        dto.setIdType(e.getIdType());
        dto.setMaskedValue(e.getMaskedValue() != null ? e.getMaskedValue() : PersonIdentifierDto.computeMaskedValue(e.getRawValue()));
        dto.setIssuingAuthority(e.getIssuingAuthority());
        dto.setVerified(e.isVerified());
        if (e.getValidFrom() != null) dto.setValidFrom(e.getValidFrom().toString());
        if (e.getValidTo() != null) dto.setValidTo(e.getValidTo().toString());
        return dto;
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
            s.setCanViewAcademics(g.isCanViewAcademics());
            s.setCanViewFees(g.isCanViewFees());
            s.setCanPayFees(g.isCanPayFees());

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

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return "[" + list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
    }
}
