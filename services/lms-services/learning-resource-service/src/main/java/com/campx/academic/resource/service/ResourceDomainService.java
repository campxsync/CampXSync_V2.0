package com.campx.academic.resource.service;

import com.campx.academic.resource.exception.*;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative Domain Service implementing ACD-08 Learning Resources business logic:
 * - Resource Management & Metadata (US-001 through US-006, US-049, US-050)
 * - Shared Object Storage Integration (US-007, US-008, US-018, US-051, US-056)
 * - Monotonic Versioning & Immutability (US-009, US-010, US-011, US-012, US-024)
 * - Policy-Driven Access Control (US-013 through US-017, US-019, US-052 through US-055)
 * - Lifecycle State Machine & Syllabus Publication (US-020 through US-025)
 * - Catalog Search, Query & Discovery (US-005, US-026 through US-030)
 * - Immutable Audit History (US-031, US-032, US-033)
 * - Reliable Outbox Events & DLQ (US-034 through US-042, US-060, US-061)
 * - Bulk Operations, Export & Intelligence (US-043, US-044, US-058, US-059, US-063)
 * - Academic Context & Disaster Recovery (US-048, US-062, US-064, US-065)
 */
public class ResourceDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceDomainService.class);

    // Primary Collections
    private final Map<String, LearningResource> resourcesById = new ConcurrentHashMap<>();
    private final Map<String, LearningResource> resourcesByTenantAndCode = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyStore = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyRecord> apiKeys = new ConcurrentHashMap<>();

    // Subsystem Adapters & Services
    private final ResourceValidationEngine validationEngine = new ResourceValidationEngine();
    private final ResourceVersionManager versionManager = new ResourceVersionManager();
    private final ResourceAccessPolicyService accessPolicyService = new ResourceAccessPolicyService();
    private final ObjectStorageAdapter objectStorageAdapter = new ObjectStorageAdapter();
    private final AuditAdapter auditAdapter = new AuditAdapter();
    private final OutboxPublisher outboxPublisher = new OutboxPublisher();
    private final ResourceQueryService queryService = new ResourceQueryService();
    private final SubjectReferenceClient subjectClient = new SubjectReferenceClient();
    private final CurriculumReferenceClient curriculumClient = new CurriculumReferenceClient();
    private final AcademicCalendarReferenceClient calendarClient = new AcademicCalendarReferenceClient();
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    public ResourceDomainService() {
        // Seed default API keys for external integration
        apiKeys.put("CAMPX-RES-KEY-2026", new ApiKeyRecord("KEY-1", "CAMPX-RES-KEY-2026", "TENANT-001", "Standard integration key"));
    }

    // ==========================================
    // RESOURCE MANAGEMENT & REGISTRATION
    // ==========================================

    /**
     * Registers a new learning resource (US-001, US-002, US-003, US-004, US-006, US-020, US-040, US-049, US-050).
     */
    public synchronized LearningResource createResource(CreateResourceRequest req, String tenantId,
                                                        String userId, String userRole,
                                                        String idempotencyKey, String correlationId) {
        // 1. Idempotency Check (US-040)
        String reqHash = calculateRequestHash(req);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String keyLookup = tenantId + ":" + idempotencyKey.trim();
            IdempotencyRecord rec = idempotencyStore.get(keyLookup);
            if (rec != null) {
                if (!rec.getRequestHash().equals(reqHash)) {
                    throw new ResourceIdempotencyConflictException("Idempotency key reused with different request payload");
                }
                LearningResource existing = resourcesById.get(rec.getResponseBody());
                if (existing != null) {
                    logger.info("[ResourceDomainService] Returning cached idempotent resource {}", existing.getId());
                    return existing;
                }
            }
        }

        // 2. Validate payload deterministically (US-004, US-050)
        validationEngine.validateCreateResource(req);

        // 3. Prevent duplicate resource code within tenant (US-006)
        String uniqueKey = tenantId + ":" + req.resourceCode.trim();
        if (resourcesByTenantAndCode.containsKey(uniqueKey)) {
            throw new ResourceDuplicateException("Resource code '" + req.resourceCode + "' already exists for tenant " + tenantId);
        }

        // 4. Validate ownership & academic context (US-003, US-049, US-064)
        if ("FACULTY".equalsIgnoreCase(userRole)) {
            if (req.departmentId == null || req.departmentId.trim().isEmpty()) {
                throw new ResourceValidationException("Faculty must specify authorized departmentId");
            }
        }
        if (req.subjectId != null && !subjectClient.isValidSubject(req.subjectId)) {
            throw new ResourceValidationException("Referenced subjectId '" + req.subjectId + "' is invalid or not registered");
        }
        if (req.curriculumId != null && !curriculumClient.isValidCurriculum(req.curriculumId)) {
            throw new ResourceValidationException("Referenced curriculumId '" + req.curriculumId + "' is invalid or not registered");
        }
        if (req.courseId != null && !curriculumClient.isValidCourse(req.courseId)) {
            throw new ResourceValidationException("Referenced courseId '" + req.courseId + "' is invalid or not registered");
        }
        if (req.academicPeriodId != null && !calendarClient.isValidPeriod(req.academicPeriodId)) {
            throw new ResourceValidationException("Referenced academicPeriodId '" + req.academicPeriodId + "' is invalid");
        }

        // 5. Build LearningResource aggregate in DRAFT state (US-020)
        String resourceId = "RES-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String now = Instant.now().toString();

        LearningResource resource = new LearningResource();
        resource.setId(resourceId);
        resource.setTenantId(tenantId);
        resource.setInstitutionId(req.institutionId != null ? req.institutionId : "INST-001");
        resource.setCampusId(req.campusId != null ? req.campusId : "CAMPUS-001");
        resource.setResourceCode(req.resourceCode.trim());
        resource.setTitle(req.title.trim());
        resource.setResourceType(ResourceType.valueOf(req.resourceType.toUpperCase()));
        resource.setSubjectId(req.subjectId);
        resource.setCourseId(req.courseId);
        resource.setCurriculumId(req.curriculumId);
        resource.setDepartmentId(req.departmentId != null ? req.departmentId : "DEP-GENERAL");
        resource.setOwnerId(userId);
        resource.setStatus(ResourceStatus.DRAFT);
        resource.setCurrentVersion(1L);
        resource.setPublishedVersion(null);
        resource.setAccessPolicyId("POL-" + resourceId);
        resource.setTags(req.tags);
        resource.setDescription(req.description);
        resource.setAcademicPeriodId(req.academicPeriodId);
        resource.setCreatedBy(userId);
        resource.setCreatedAt(now);
        resource.setUpdatedBy(userId);
        resource.setUpdatedAt(now);
        resource.setCorrelationId(correlationId);

        // 6. Register Version 1 (US-001, US-009)
        versionManager.registerInitialVersion(resource, req, userId);

        // 7. Register Initial Access Grant if specified (US-013)
        if (req.initialAccessScope != null) {
            CreateAccessGrantRequest grantReq = new CreateAccessGrantRequest();
            grantReq.principalType = "ROLE";
            grantReq.principalId = "*";
            grantReq.scopeType = req.initialAccessScope;
            grantReq.scopeId = req.initialScopeId;
            grantReq.permission = "VIEW";
            grantReq.effect = "ALLOW";
            accessPolicyService.createGrant(resource, grantReq, userId);
        }

        // 8. Commit to collections
        resourcesById.put(resourceId, resource);
        resourcesByTenantAndCode.put(uniqueKey, resource);

        // 9. Append Audit History (US-031)
        auditAdapter.recordHistory(tenantId, resourceId, 1L, "CREATE", null,
                ResourceStatus.DRAFT.name(), userId, "Initial registration", null, correlationId);

        // 10. Transactional Outbox Event (US-034, US-039)
        String eventPayload = "{\"resourceId\":\"" + resourceId + "\",\"resourceCode\":\"" + resource.getResourceCode() +
                "\",\"title\":\"" + resource.getTitle() + "\",\"resourceType\":\"" + resource.getResourceType() + "\"}";
        outboxPublisher.recordOutboxEvent(tenantId, resourceId, "LearningResourceCreated", eventPayload, correlationId);

        // 11. Record Idempotency Record (US-040)
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            IdempotencyRecord rec = new IdempotencyRecord();
            rec.setId("IDEM-" + UUID.randomUUID().toString().substring(0, 8));
            rec.setTenantId(tenantId);
            rec.setIdempotencyKey(idempotencyKey.trim());
            rec.setRequestHash(reqHash);
            rec.setOperation("CREATE_RESOURCE");
            rec.setResponseStatus(201);
            rec.setResponseBody(resourceId);
            rec.setCreatedAt(now);
            idempotencyStore.put(tenantId + ":" + idempotencyKey.trim(), rec);
        }

        metricsCollector.recordRegistration();
        logger.info("[ResourceDomainService] Successfully registered resource {} with code {}", resourceId, resource.getResourceCode());
        return resource;
    }

    /**
     * Updates resource metadata with optimistic locking (US-012).
     */
    public synchronized LearningResource updateResource(String resourceId, UpdateResourceRequest req,
                                                        String tenantId, String userId,
                                                        String userRole, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        // Optimistic concurrency check (US-012)
        if (req.expectedVersion != null && req.expectedVersion != resource.getCurrentVersion()) {
            throw new ResourceVersionConflictException("Version conflict: expected " + req.expectedVersion +
                    " but current version is " + resource.getCurrentVersion());
        }

        if (req.title != null && !req.title.trim().isEmpty()) {
            resource.setTitle(req.title.trim());
        }
        if (req.description != null) {
            resource.setDescription(req.description);
        }
        if (req.tags != null) {
            resource.setTags(req.tags);
        }

        resource.setUpdatedAt(Instant.now().toString());
        resource.setUpdatedBy(userId);

        auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                "METADATA_UPDATE", resource.getStatus().name(), resource.getStatus().name(),
                userId, "Resource metadata update", null, correlationId);

        logger.info("[ResourceDomainService] Updated metadata for resource {}", resourceId);
        return resource;
    }

    // ==========================================
    // VERSIONING & CONTENT
    // ==========================================

    /**
     * Creates a new monotonic version of a resource (US-009, US-024, US-056).
     */
    public synchronized ResourceVersion createVersion(String resourceId, CreateVersionRequest req,
                                                      String tenantId, String userId,
                                                      String userRole, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        // Validate content reference
        validationEngine.validateCreateVersion(req);

        // Duplicate content detection by checksum (US-056)
        if (versionManager.hasDuplicateContent(tenantId, req.checksum, resourceId)) {
            logger.warn("[ResourceDomainService] Duplicate content detected for checksum {} across tenant {}", req.checksum, tenantId);
        }

        // Verify storage object reference (US-008)
        objectStorageAdapter.verifyObjectReference(req.storageProvider, req.storageObjectRef);

        ResourceVersion newVersion = versionManager.createVersion(resource, req, userId);

        auditAdapter.recordHistory(tenantId, resourceId, newVersion.getVersionNo(),
                "VERSION_CREATE", resource.getStatus().name(), resource.getStatus().name(),
                userId, req.changeSummary, null, correlationId);

        metricsCollector.recordVersionCreation();
        logger.info("[ResourceDomainService] Created version {} for resource {}", newVersion.getVersionNo(), resourceId);
        return newVersion;
    }

    /**
     * Restores an eligible historical version (US-011).
     */
    public synchronized ResourceVersion restoreVersion(String resourceId, long versionNo,
                                                       String tenantId, String userId,
                                                       String userRole, String reason,
                                                       String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        ResourceVersion restored = versionManager.restoreVersion(resource, versionNo, userId, reason);

        auditAdapter.recordHistory(tenantId, resourceId, restored.getVersionNo(),
                "RESTORE", resource.getStatus().name(), resource.getStatus().name(),
                userId, "Restored from version " + versionNo + (reason != null ? ": " + reason : ""),
                null, correlationId);

        logger.info("[ResourceDomainService] Restored version {} as version {} on resource {}",
                versionNo, restored.getVersionNo(), resourceId);
        return restored;
    }

    // ==========================================
    // LIFECYCLE: PUBLISH, ARCHIVE, APPROVE
    // ==========================================

    /**
     * Publishes a resource version (US-021, US-022, US-025, US-035, US-037).
     */
    public synchronized LearningResource publishResource(String resourceId, PublishResourceRequest req,
                                                         String tenantId, String userId,
                                                         String userRole, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        // Authorization check: Admin, Department Head, or Faculty owner
        boolean isPublisher = "ACADEMIC_ADMIN".equalsIgnoreCase(userRole) ||
                "SUPER_ADMIN".equalsIgnoreCase(userRole) ||
                "DEPARTMENT_HEAD".equalsIgnoreCase(userRole) ||
                ("FACULTY".equalsIgnoreCase(userRole) && userId.equalsIgnoreCase(resource.getOwnerId()));
        if (!isPublisher) {
            throw new ResourcePublishForbiddenException("Role '" + userRole + "' is not permitted to publish academic resources");
        }

        // Optimistic concurrency check (US-012)
        if (req != null && req.expectedVersion != null && req.expectedVersion != resource.getCurrentVersion()) {
            throw new ResourceVersionConflictException("Cannot publish: expectedVersion " + req.expectedVersion +
                    " does not match currentVersion " + resource.getCurrentVersion());
        }

        // Verify content reference of the active version
        ResourceVersion currentVer = versionManager.getVersion(resourceId, resource.getCurrentVersion());
        objectStorageAdapter.verifyObjectReference(currentVer.getStorageProvider(), currentVer.getObjectKey());

        ResourceStatus fromStatus = resource.getStatus();
        resource.setStatus(ResourceStatus.PUBLISHED);
        resource.setPublishedVersion(currentVer.getVersionNo());
        currentVer.setStatus(VersionStatus.PUBLISHED);
        currentVer.setPublishedAt(Instant.now().toString());

        resource.setUpdatedAt(Instant.now().toString());
        resource.setUpdatedBy(userId);

        // Audit Trail (US-031)
        auditAdapter.recordHistory(tenantId, resourceId, currentVer.getVersionNo(), "PUBLISH",
                fromStatus.name(), ResourceStatus.PUBLISHED.name(), userId,
                req != null ? req.comment : "Published authoritative resource",
                req != null ? req.approvalRef : null, correlationId);

        // Outbox event: LearningResourcePublished (US-035)
        String pubPayload = "{\"resourceId\":\"" + resourceId + "\",\"versionNo\":" + currentVer.getVersionNo() +
                ",\"status\":\"PUBLISHED\",\"publishedAt\":\"" + currentVer.getPublishedAt() + "\"}";
        outboxPublisher.recordOutboxEvent(tenantId, resourceId, "LearningResourcePublished", pubPayload, correlationId);

        // Syllabus Publication Event (US-025, US-037)
        if (resource.getResourceType() == ResourceType.SYLLABUS) {
            String syllabusPayload = "{\"resourceId\":\"" + resourceId + "\",\"resourceCode\":\"" + resource.getResourceCode() +
                    "\",\"versionNo\":" + currentVer.getVersionNo() + ",\"curriculumId\":\"" + resource.getCurriculumId() +
                    "\",\"subjectId\":\"" + resource.getSubjectId() + "\"}";
            outboxPublisher.recordOutboxEvent(tenantId, resourceId, "SyllabusPublished", syllabusPayload, correlationId);
            logger.info("[ResourceDomainService] Emitted SyllabusPublished event for syllabus {}", resourceId);
        }

        metricsCollector.recordPublication();
        logger.info("[ResourceDomainService] Published resource {} version {}", resourceId, currentVer.getVersionNo());
        return resource;
    }

    /**
     * Archives a published resource (US-023, US-036).
     */
    public synchronized LearningResource archiveResource(String resourceId, String tenantId,
                                                         String userId, String userRole,
                                                         String reason, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        ResourceStatus fromStatus = resource.getStatus();
        resource.setStatus(ResourceStatus.ARCHIVED);
        resource.setUpdatedAt(Instant.now().toString());
        resource.setUpdatedBy(userId);

        auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                "ARCHIVE", fromStatus.name(), ResourceStatus.ARCHIVED.name(), userId,
                reason != null ? reason : "Archived resource", null, correlationId);

        String archivePayload = "{\"resourceId\":\"" + resourceId + "\",\"status\":\"ARCHIVED\"}";
        outboxPublisher.recordOutboxEvent(tenantId, resourceId, "LearningResourceArchived", archivePayload, correlationId);

        metricsCollector.recordArchive();
        logger.info("[ResourceDomainService] Archived resource {}", resourceId);
        return resource;
    }

    // ==========================================
    // ACCESS CONTROL & RETRIEVAL
    // ==========================================

    /**
     * Adds an access grant (US-013, US-014, US-052, US-054).
     */
    public ResourceAccessGrant addAccessGrant(String resourceId, CreateAccessGrantRequest req,
                                              String tenantId, String userId,
                                              String userRole, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        validationEngine.validateAccessGrant(req);
        ResourceAccessGrant grant = accessPolicyService.createGrant(resource, req, userId);

        auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                "ACCESS_GRANT", null, grant.getEffect().name(), userId,
                "Grant: " + grant.getPermission() + " to " + grant.getPrincipalType() + ":" + grant.getPrincipalId(),
                null, correlationId);

        return grant;
    }

    /**
     * Updates an access grant (US-015).
     */
    public ResourceAccessGrant updateAccessGrant(String resourceId, String accessId, CreateAccessGrantRequest req,
                                                 String tenantId, String userId, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        ResourceAccessGrant updated = accessPolicyService.updateGrant(accessId, req, userId);
        auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                "ACCESS_UPDATE", null, updated.getEffect().name(), userId, "Updated grant " + accessId, null, correlationId);
        return updated;
    }

    /**
     * Revokes an access grant (US-016).
     */
    public ResourceAccessGrant revokeAccessGrant(String resourceId, String accessId,
                                                 String tenantId, String userId,
                                                 String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        ResourceAccessGrant revoked = accessPolicyService.revokeGrant(accessId, userId);
        auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                "ACCESS_REVOKE", "ACTIVE", "REVOKED", userId, "Revoked grant " + accessId, null, correlationId);
        return revoked;
    }

    /**
     * Downloads resource content with secure short-lived URL after access evaluation (US-017, US-018, US-019, US-051).
     */
    public DownloadResponse downloadResource(String resourceId, Long versionNo, String tenantId,
                                             String userId, String userRole, String deptId,
                                             String batchId, String programId, String correlationId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);

        // Access Policy Evaluation (US-017, US-019, US-053)
        boolean allowed = accessPolicyService.checkAccess(resource, userId, userRole, deptId, batchId, programId, Permission.DOWNLOAD);
        if (!allowed) {
            metricsCollector.recordPolicyDenial();
            auditAdapter.recordHistory(tenantId, resourceId, resource.getCurrentVersion(),
                    "ACCESS_DENIED", resource.getStatus().name(), resource.getStatus().name(),
                    userId, "Policy denial on download request", null, correlationId);
            throw new ResourcePolicyDeniedException("Access denied: You do not have permission to download this resource");
        }

        long targetVer = (versionNo != null) ? versionNo : resource.getCurrentVersion();
        ResourceVersion ver = versionManager.getVersion(resourceId, targetVer);

        DownloadResponse resp = objectStorageAdapter.generateSignedRetrievalReference(ver, userId, tenantId);
        queryService.recordDownload(resourceId, deptId);
        metricsCollector.recordDownload();

        logger.info("[ResourceDomainService] Authorized download for resource {} version {} by {}", resourceId, targetVer, userId);
        return resp;
    }

    // ==========================================
    // CATALOG SEARCH & QUERIES
    // ==========================================

    public LearningResource getResourceById(String resourceId) {
        LearningResource res = resourcesById.get(resourceId);
        if (res == null) {
            throw new ResourceNotFoundException("Learning resource not found with ID: " + resourceId);
        }
        return res;
    }

    public List<LearningResource> searchResources(ResourceSearchFilter filter, String tenantId,
                                                  String userId, String userRole, String deptId,
                                                  String batchId, String progId) {
        List<LearningResource> matches = queryService.search(resourcesById.values(), filter, tenantId);

        // Filter out unpublished resources for students (US-017)
        if ("STUDENT".equalsIgnoreCase(userRole)) {
            matches.removeIf(r -> r.getStatus() != ResourceStatus.PUBLISHED ||
                    !accessPolicyService.checkAccess(r, userId, userRole, deptId, batchId, progId, Permission.VIEW));
        }

        return matches;
    }

    public Set<String> getAvailableTags(String tenantId) {
        return queryService.getTags(resourcesById.values(), tenantId);
    }

    public ResourceUsageStats getUsageStats(String resourceId, String tenantId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);
        return queryService.getUsageStats(resourceId);
    }

    public AdvisoryInsightsResponse getAdvisoryInsights(String tenantId) {
        return queryService.generateInsights(resourcesById.values(), tenantId);
    }

    public List<ResourceVersion> getResourceVersions(String resourceId, String tenantId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);
        return versionManager.listVersions(resourceId);
    }

    public ResourceVersion getVersionById(String versionId, String tenantId) {
        ResourceVersion v = versionManager.getVersionById(versionId);
        LearningResource res = getResourceById(v.getResourceId());
        verifyTenant(res, tenantId);
        return v;
    }

    public List<ResourceAccessGrant> getAccessGrants(String resourceId, String tenantId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);
        return accessPolicyService.getGrants(resourceId);
    }

    public List<ResourceHistory> getResourceHistory(String resourceId, String tenantId) {
        LearningResource resource = getResourceById(resourceId);
        verifyTenant(resource, tenantId);
        return auditAdapter.getHistory(resourceId);
    }

    // ==========================================
    // EVENT CONSUMPTION & DLQ REPLAY
    // ==========================================

    /**
     * Consumes external domain events idempotently (US-038, US-060).
     */
    public boolean consumeEvent(Map<String, Object> event) {
        if (event == null) return false;
        String eventId = (String) event.get("eventId");
        String eventType = (String) event.get("eventType");

        if (eventId == null || eventType == null) return false;

        // Idempotency check for incoming events (US-060)
        String lookupKey = "CONSUMED_EVENT:" + eventId;
        if (idempotencyStore.containsKey(lookupKey)) {
            logger.info("[ResourceDomainService] Ignored duplicate consumed event {}", eventId);
            return true;
        }

        if ("SubjectCreated".equalsIgnoreCase(eventType)) {
            String subjectId = (String) event.get("subjectId");
            if (subjectId != null) {
                subjectClient.registerSubject(subjectId);
                logger.info("[ResourceDomainService] Consumed SubjectCreated event: registered subject {}", subjectId);
            }
        } else if ("CurriculumPublished".equalsIgnoreCase(eventType)) {
            String curriculumId = (String) event.get("curriculumId");
            if (curriculumId != null) {
                curriculumClient.registerCurriculum(curriculumId);
                logger.info("[ResourceDomainService] Consumed CurriculumPublished event: registered curriculum {}", curriculumId);
            }
        }

        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setId("IDEM-" + eventId);
        rec.setTenantId("GLOBAL");
        rec.setIdempotencyKey(lookupKey);
        rec.setRequestHash(eventId);
        rec.setOperation("CONSUME_EVENT");
        rec.setResponseStatus(200);
        rec.setCreatedAt(Instant.now().toString());
        idempotencyStore.put(lookupKey, rec);

        return true;
    }

    /**
     * Replays a dead letter event (US-061).
     */
    public boolean replayDeadLetterEvent(String eventId) {
        return outboxPublisher.replayDeadLetterEvent(eventId);
    }

    // ==========================================
    // BULK OPERATIONS, DR & RETENTION
    // ==========================================

    /**
     * Bulk import of resources (US-058).
     */
    public BulkImportResult bulkImport(BulkImportRequest req, String tenantId, String userId, String userRole, String correlationId) {
        BulkImportResult result = new BulkImportResult();
        result.importJobId = (req.importJobId != null) ? req.importJobId : "JOB-" + UUID.randomUUID().toString().substring(0, 8);
        result.totalRecords = req.items != null ? req.items.size() : 0;

        if (req.items != null) {
            for (CreateResourceRequest item : req.items) {
                try {
                    createResource(item, tenantId, userId, userRole, null, correlationId);
                    result.successfulRecords++;
                } catch (Exception e) {
                    result.failedRecords++;
                    result.errors.add("Failed record '" + item.resourceCode + "': " + e.getMessage());
                }
            }
        }

        logger.info("[ResourceDomainService] Bulk import completed: {} success, {} failed",
                result.successfulRecords, result.failedRecords);
        return result;
    }

    /**
     * Disaster recovery check verifying metadata vs object storage consistency (US-048, US-065).
     */
    public DisasterRecoveryValidationResult validateDisasterRecovery(String tenantId) {
        DisasterRecoveryValidationResult result = new DisasterRecoveryValidationResult();
        int total = 0;
        int valid = 0;
        int broken = 0;

        for (LearningResource r : resourcesById.values()) {
            if (tenantId == null || tenantId.equals(r.getTenantId())) {
                List<ResourceVersion> versions = versionManager.listVersions(r.getId());
                for (ResourceVersion v : versions) {
                    total++;
                    try {
                        boolean ok = objectStorageAdapter.verifyObjectReference(v.getStorageProvider(), v.getObjectKey());
                        if (ok) valid++;
                        else {
                            broken++;
                            result.inconsistencies.add("Broken reference on resource " + r.getId() + " ver " + v.getVersionNo());
                        }
                    } catch (Exception ex) {
                        broken++;
                        result.inconsistencies.add("Unreachable storage for resource " + r.getId() + " ver " + v.getVersionNo() + ": " + ex.getMessage());
                    }
                }
            }
        }

        result.totalChecked = total;
        result.validReferences = valid;
        result.brokenReferences = broken;
        result.consistent = (broken == 0);
        return result;
    }

    /**
     * API key validation for external integrations.
     */
    public ApiKeyRecord validateApiKey(String apiKey, String tenantId) {
        if (apiKey == null) return null;
        ApiKeyRecord rec = apiKeys.get(apiKey.trim());
        if (rec != null && rec.isActive()) {
            if (tenantId == null || tenantId.equals(rec.getTenantId())) {
                return rec;
            }
        }
        return null;
    }

    public OutboxPublisher getOutboxPublisher() {
        return outboxPublisher;
    }

    public ResourceVersionManager getVersionManager() {
        return versionManager;
    }

    public ResourceAccessPolicyService getAccessPolicyService() {
        return accessPolicyService;
    }

    public ObjectStorageAdapter getObjectStorageAdapter() {
        return objectStorageAdapter;
    }

    private void verifyTenant(LearningResource resource, String tenantId) {
        if (tenantId != null && !tenantId.equalsIgnoreCase(resource.getTenantId())) {
            throw new ResourcePolicyDeniedException("Access denied: cross-tenant access violation");
        }
    }

    private String calculateRequestHash(CreateResourceRequest req) {
        if (req == null) return "EMPTY";
        return String.valueOf(Objects.hash(req.resourceCode, req.title, req.resourceType, req.subjectId, req.storageObjectRef));
    }
}
