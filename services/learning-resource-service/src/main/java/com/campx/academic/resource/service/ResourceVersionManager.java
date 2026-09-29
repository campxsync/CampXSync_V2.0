package com.campx.academic.resource.service;

import com.campx.academic.resource.exception.ResourceNotFoundException;
import com.campx.academic.resource.exception.ResourceVersionConflictException;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages monotonic immutable versions for ACD-08 Learning Resources:
 * - Monotonic version increments (US-009)
 * - Version retrieval & inspection (US-010)
 * - Version restoration without destructive deletion (US-011)
 * - Optimistic concurrency checks (US-012)
 * - Strict immutability of published versions (US-024)
 * - Duplicate content detection via checksum (US-056)
 */
public class ResourceVersionManager {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceVersionManager.class);

    // Map: resourceId -> Map<versionNo, ResourceVersion>
    private final Map<String, Map<Long, ResourceVersion>> versionsByResource = new ConcurrentHashMap<>();
    // Map: versionId -> ResourceVersion
    private final Map<String, ResourceVersion> versionsById = new ConcurrentHashMap<>();

    /**
     * Creates and registers an immutable version record.
     */
    public ResourceVersion createVersion(LearningResource resource, CreateVersionRequest req, String userId) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }

        // Optimistic concurrency check (US-012)
        if (req.expectedVersion != null && req.expectedVersion != resource.getCurrentVersion()) {
            throw new ResourceVersionConflictException("Optimistic concurrency check failed: expectedVersion " +
                    req.expectedVersion + " does not match currentVersion " + resource.getCurrentVersion());
        }

        long nextVersionNo = resource.getCurrentVersion() + 1;
        String versionId = "VER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ResourceVersion version = new ResourceVersion();
        version.setId(versionId);
        version.setTenantId(resource.getTenantId());
        version.setResourceId(resource.getId());
        version.setVersionNo(nextVersionNo);
        version.setStorageProvider(req.storageProvider != null ? req.storageProvider : "SHARED_BLOB");
        version.setObjectKey(req.storageObjectRef);
        version.setFileName(req.fileName != null ? req.fileName : "resource_v" + nextVersionNo + ".bin");
        version.setMimeType(req.mimeType != null ? req.mimeType : "application/octet-stream");
        version.setFileSize(req.fileSize != null ? req.fileSize : 0L);
        version.setChecksum(req.checksum != null ? req.checksum : "");
        version.setContentHash(req.contentHash != null ? req.contentHash : req.checksum);
        version.setCreatedBy(userId);
        version.setCreatedAt(Instant.now().toString());
        version.setChangeSummary(req.changeSummary);
        version.setStatus(VersionStatus.DRAFT);

        // Save into stores
        versionsByResource.computeIfAbsent(resource.getId(), k -> new ConcurrentHashMap<>()).put(nextVersionNo, version);
        versionsById.put(versionId, version);

        // Update resource current version pointer
        resource.setCurrentVersion(nextVersionNo);
        resource.setUpdatedAt(Instant.now().toString());
        resource.setUpdatedBy(userId);

        logger.info("[ResourceVersionManager] Registered version {} (id={}) for resource {}", nextVersionNo, versionId, resource.getId());
        return version;
    }

    /**
     * Registers initial version 1 on resource registration.
     */
    public ResourceVersion registerInitialVersion(LearningResource resource, CreateResourceRequest req, String userId) {
        long versionNo = 1L;
        String versionId = "VER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ResourceVersion version = new ResourceVersion();
        version.setId(versionId);
        version.setTenantId(resource.getTenantId());
        version.setResourceId(resource.getId());
        version.setVersionNo(versionNo);
        version.setStorageProvider(req.storageProvider != null ? req.storageProvider : "SHARED_BLOB");
        version.setObjectKey(req.storageObjectRef != null ? req.storageObjectRef : "default/" + resource.getResourceCode());
        version.setFileName(req.fileName != null ? req.fileName : resource.getResourceCode() + "_v1");
        version.setMimeType(req.mimeType != null ? req.mimeType : "application/pdf");
        version.setFileSize(req.fileSize != null ? req.fileSize : 1024L);
        version.setChecksum(req.checksum != null ? req.checksum : "0000000000000000000000000000000000000000000000000000000000000000");
        version.setContentHash(req.contentHash != null ? req.contentHash : version.getChecksum());
        version.setCreatedBy(userId);
        version.setCreatedAt(Instant.now().toString());
        version.setChangeSummary("Initial registration");
        version.setStatus(VersionStatus.DRAFT);

        versionsByResource.computeIfAbsent(resource.getId(), k -> new ConcurrentHashMap<>()).put(versionNo, version);
        versionsById.put(versionId, version);

        resource.setCurrentVersion(versionNo);
        return version;
    }

    /**
     * Retrieves version by unique ID (US-010).
     */
    public ResourceVersion getVersionById(String versionId) {
        ResourceVersion ver = versionsById.get(versionId);
        if (ver == null) {
            throw new ResourceNotFoundException("Resource version not found with ID: " + versionId);
        }
        return ver;
    }

    /**
     * Retrieves specific version number for a resource.
     */
    public ResourceVersion getVersion(String resourceId, long versionNo) {
        Map<Long, ResourceVersion> resVersions = versionsByResource.get(resourceId);
        if (resVersions == null || !resVersions.containsKey(versionNo)) {
            throw new ResourceNotFoundException("Version " + versionNo + " not found for resource " + resourceId);
        }
        return resVersions.get(versionNo);
    }

    /**
     * Lists all versions for a resource.
     */
    public List<ResourceVersion> listVersions(String resourceId) {
        Map<Long, ResourceVersion> map = versionsByResource.get(resourceId);
        if (map == null) return Collections.emptyList();
        List<ResourceVersion> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingLong(ResourceVersion::getVersionNo));
        return list;
    }

    /**
     * Restores an eligible historical version without destructive deletion (US-011).
     * Creates a new version reflecting the restored snapshot state.
     */
    public ResourceVersion restoreVersion(LearningResource resource, long targetVersionNo, String userId, String reason) {
        ResourceVersion target = getVersion(resource.getId(), targetVersionNo);

        CreateVersionRequest restoreReq = new CreateVersionRequest();
        restoreReq.storageObjectRef = target.getObjectKey();
        restoreReq.storageProvider = target.getStorageProvider();
        restoreReq.fileName = target.getFileName();
        restoreReq.mimeType = target.getMimeType();
        restoreReq.fileSize = target.getFileSize();
        restoreReq.checksum = target.getChecksum();
        restoreReq.contentHash = target.getContentHash();
        restoreReq.changeSummary = "Restored from version " + targetVersionNo + (reason != null ? ": " + reason : "");

        ResourceVersion promoted = createVersion(resource, restoreReq, userId);
        logger.info("[ResourceVersionManager] Restored version {} as new version {} for resource {}",
                targetVersionNo, promoted.getVersionNo(), resource.getId());
        return promoted;
    }

    /**
     * Detects if content with the identical checksum already exists within the tenant/resource (US-056).
     */
    public boolean hasDuplicateContent(String tenantId, String checksum, String excludeResourceId) {
        if (checksum == null || checksum.trim().isEmpty()) return false;
        for (ResourceVersion v : versionsById.values()) {
            if (tenantId.equals(v.getTenantId()) && checksum.equalsIgnoreCase(v.getChecksum())) {
                if (excludeResourceId == null || !v.getResourceId().equals(excludeResourceId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
