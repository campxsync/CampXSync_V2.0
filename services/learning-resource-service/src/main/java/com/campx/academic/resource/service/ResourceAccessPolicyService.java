package com.campx.academic.resource.service;

import com.campx.academic.resource.exception.ResourceNotFoundException;
import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Access policy and authorization engine implementing RBAC and ABAC:
 * - Access rule registration, update, revocation (US-013, US-014, US-015, US-016)
 * - Strict DENY precedence evaluation (US-019, US-053)
 * - Time window enforcement (validFrom/validTo) (US-052)
 * - Policy version tracking (US-054)
 * - Scoped authorization: department, subject, course, batch, program (US-055)
 */
public class ResourceAccessPolicyService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceAccessPolicyService.class);

    // Map: accessId -> ResourceAccessGrant
    private final Map<String, ResourceAccessGrant> grantsById = new ConcurrentHashMap<>();
    // Map: resourceId -> List<ResourceAccessGrant>
    private final Map<String, List<ResourceAccessGrant>> grantsByResource = new ConcurrentHashMap<>();

    private final AtomicLong policyVersionCounter = new AtomicLong(1);

    /**
     * Creates a new access grant rule for a resource.
     */
    public ResourceAccessGrant createGrant(LearningResource resource, CreateAccessGrantRequest req, String grantedBy) {
        String accessId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long version = policyVersionCounter.incrementAndGet();

        ResourceAccessGrant grant = new ResourceAccessGrant();
        grant.setId(accessId);
        grant.setTenantId(resource.getTenantId());
        grant.setResourceId(resource.getId());
        grant.setPrincipalType(PrincipalType.valueOf(req.principalType.toUpperCase()));
        grant.setPrincipalId(req.principalId);
        grant.setScopeType(req.scopeType != null ? ScopeType.valueOf(req.scopeType.toUpperCase()) : ScopeType.GLOBAL);
        grant.setScopeId(req.scopeId);
        grant.setPermission(Permission.valueOf(req.permission.toUpperCase()));
        grant.setEffect(req.effect != null ? AccessEffect.valueOf(req.effect.toUpperCase()) : AccessEffect.ALLOW);
        grant.setValidFrom(req.validFrom);
        grant.setValidTo(req.validTo);
        grant.setStatus(GrantStatus.ACTIVE);
        grant.setGrantedBy(grantedBy);
        grant.setGrantedAt(Instant.now().toString());
        grant.setPolicyVersion(version);

        grantsById.put(accessId, grant);
        grantsByResource.computeIfAbsent(resource.getId(), k -> Collections.synchronizedList(new ArrayList<>())).add(grant);

        logger.info("[ResourceAccessPolicyService] Added grant {} for resource {} (effect={}, perm={}, policyVer={})",
                accessId, resource.getId(), grant.getEffect(), grant.getPermission(), version);
        return grant;
    }

    /**
     * Updates an existing access grant.
     */
    public ResourceAccessGrant updateGrant(String accessId, CreateAccessGrantRequest req, String updatedBy) {
        ResourceAccessGrant grant = grantsById.get(accessId);
        if (grant == null || grant.getStatus() == GrantStatus.REVOKED) {
            throw new ResourceNotFoundException("Active access grant not found with ID: " + accessId);
        }

        if (req.permission != null) {
            grant.setPermission(Permission.valueOf(req.permission.toUpperCase()));
        }
        if (req.effect != null) {
            grant.setEffect(AccessEffect.valueOf(req.effect.toUpperCase()));
        }
        if (req.validFrom != null) {
            grant.setValidFrom(req.validFrom);
        }
        if (req.validTo != null) {
            grant.setValidTo(req.validTo);
        }
        grant.setPolicyVersion(policyVersionCounter.incrementAndGet());
        grant.setGrantedBy(updatedBy);
        grant.setGrantedAt(Instant.now().toString());

        logger.info("[ResourceAccessPolicyService] Updated grant {} (new policyVer={})", accessId, grant.getPolicyVersion());
        return grant;
    }

    /**
     * Revokes an access grant (US-016).
     */
    public ResourceAccessGrant revokeGrant(String accessId, String revokedBy) {
        ResourceAccessGrant grant = grantsById.get(accessId);
        if (grant == null) {
            throw new ResourceNotFoundException("Access grant not found with ID: " + accessId);
        }
        grant.setStatus(GrantStatus.REVOKED);
        grant.setRevokedBy(revokedBy);
        grant.setRevokedAt(Instant.now().toString());
        grant.setPolicyVersion(policyVersionCounter.incrementAndGet());

        logger.info("[ResourceAccessPolicyService] Revoked grant {} by {}", accessId, revokedBy);
        return grant;
    }

    /**
     * Lists active grants for a resource.
     */
    public List<ResourceAccessGrant> getGrants(String resourceId) {
        List<ResourceAccessGrant> list = grantsByResource.get(resourceId);
        if (list == null) return Collections.emptyList();
        List<ResourceAccessGrant> active = new ArrayList<>();
        synchronized (list) {
            for (ResourceAccessGrant g : list) {
                if (g.getStatus() == GrantStatus.ACTIVE) {
                    active.add(g);
                }
            }
        }
        return active;
    }

    /**
     * Evaluates access decision using ABAC/RBAC and DENY-precedence (US-017, US-019, US-053).
     */
    public boolean checkAccess(LearningResource resource, String userId, String userRole,
                               String departmentId, String batchId, String programId,
                               Permission requiredPermission) {
        if (resource == null) return false;

        // 1. Super Admin and Academic Admin have default administrative allowance
        if ("SUPER_ADMIN".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole) ||
                "ACADEMIC_ADMIN".equalsIgnoreCase(userRole) || "SYSTEM".equalsIgnoreCase(userRole)) {
            return true;
        }

        // 2. Department Head for the owning department
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(userRole) || "HOD".equalsIgnoreCase(userRole)) {
            if (departmentId != null && departmentId.equalsIgnoreCase(resource.getDepartmentId())) {
                return true;
            }
        }

        // 3. Resource owner
        if (userId != null && userId.equalsIgnoreCase(resource.getOwnerId())) {
            return true;
        }

        // 4. Non-owners viewing unpublished content
        if (resource.getStatus() != ResourceStatus.PUBLISHED) {
            return false;
        }

        List<ResourceAccessGrant> grants = grantsByResource.get(resource.getId());
        if (grants == null || grants.isEmpty()) {
            // Default institutional catalog visibility for published resources
            return "STUDENT".equalsIgnoreCase(userRole) || "FACULTY".equalsIgnoreCase(userRole);
        }

        Instant now = Instant.now();
        boolean hasAllow = false;

        synchronized (grants) {
            for (ResourceAccessGrant grant : grants) {
                if (grant.getStatus() != GrantStatus.ACTIVE) continue;

                // Validate time window (US-052)
                if (grant.getValidFrom() != null && !grant.getValidFrom().isEmpty()) {
                    try {
                        if (now.isBefore(Instant.parse(grant.getValidFrom()))) continue;
                    } catch (Exception ignored) {}
                }
                if (grant.getValidTo() != null && !grant.getValidTo().isEmpty()) {
                    try {
                        if (now.isAfter(Instant.parse(grant.getValidTo()))) continue;
                    } catch (Exception ignored) {}
                }

                // Check permission level
                if (!permissionMatches(grant.getPermission(), requiredPermission)) {
                    continue;
                }

                // Check principal match
                boolean principalMatches = matchPrincipal(grant, userId, userRole);
                if (!principalMatches) continue;

                // Check scope match (US-055)
                boolean scopeMatches = matchScope(grant, resource, departmentId, batchId, programId);
                if (!scopeMatches) continue;

                // DENY precedence (US-019, US-053)
                if (grant.getEffect() == AccessEffect.DENY) {
                    logger.warn("[ResourceAccessPolicyService] DENY rule {} matched caller {} on resource {}",
                            grant.getId(), userId, resource.getId());
                    return false;
                }

                if (grant.getEffect() == AccessEffect.ALLOW) {
                    hasAllow = true;
                }
            }
        }

        return hasAllow;
    }

    private boolean permissionMatches(Permission grantPerm, Permission reqPerm) {
        if (grantPerm == Permission.MANAGE) return true;
        if (grantPerm == Permission.DOWNLOAD && reqPerm == Permission.VIEW) return true;
        return grantPerm == reqPerm;
    }

    private boolean matchPrincipal(ResourceAccessGrant grant, String userId, String userRole) {
        if (grant.getPrincipalType() == PrincipalType.ROLE) {
            return "*".equals(grant.getPrincipalId()) || grant.getPrincipalId().equalsIgnoreCase(userRole);
        }
        if (grant.getPrincipalType() == PrincipalType.USER) {
            return "*".equals(grant.getPrincipalId()) || grant.getPrincipalId().equalsIgnoreCase(userId);
        }
        return true;
    }

    private boolean matchScope(ResourceAccessGrant grant, LearningResource resource,
                               String callerDept, String callerBatch, String callerProg) {
        if (grant.getScopeType() == ScopeType.GLOBAL) return true;
        if (grant.getScopeType() == ScopeType.DEPARTMENT) {
            return grant.getScopeId() == null || grant.getScopeId().equalsIgnoreCase(callerDept) ||
                    grant.getScopeId().equalsIgnoreCase(resource.getDepartmentId());
        }
        if (grant.getScopeType() == ScopeType.BATCH) {
            return grant.getScopeId() == null || grant.getScopeId().equalsIgnoreCase(callerBatch);
        }
        if (grant.getScopeType() == ScopeType.PROGRAM) {
            return grant.getScopeId() == null || grant.getScopeId().equalsIgnoreCase(callerProg);
        }
        if (grant.getScopeType() == ScopeType.SUBJECT) {
            return grant.getScopeId() == null || grant.getScopeId().equalsIgnoreCase(resource.getSubjectId());
        }
        if (grant.getScopeType() == ScopeType.COURSE) {
            return grant.getScopeId() == null || grant.getScopeId().equalsIgnoreCase(resource.getCourseId());
        }
        return true;
    }
}
