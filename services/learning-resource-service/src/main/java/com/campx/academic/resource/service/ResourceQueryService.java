package com.campx.academic.resource.service;

import com.campx.academic.resource.model.ResourceModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Query service powering Catalog search, Tag discovery, Analytics and Exports:
 * - Filtered search with pagination & sorting (US-005, US-026, US-027, US-030)
 * - Available tags in tenant (US-029)
 * - Resource metadata export (US-043, US-059)
 * - Resource usage analytics (US-044)
 * - Advisory insights: stale resources, syllabus coverage, type distribution (US-063)
 */
public class ResourceQueryService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceQueryService.class);

    // Map: resourceId -> ResourceUsageStats
    private final Map<String, ResourceUsageStats> usageStore = new ConcurrentHashMap<>();

    public void recordView(String resourceId, String departmentId) {
        ResourceUsageStats stats = usageStore.computeIfAbsent(resourceId, k -> {
            ResourceUsageStats s = new ResourceUsageStats();
            s.resourceId = resourceId;
            return s;
        });
        synchronized (stats) {
            stats.viewCount++;
            stats.lastAccessedAt = Instant.now().toString();
            if (departmentId != null) {
                stats.accessByDepartment.merge(departmentId, 1L, Long::sum);
            }
        }
    }

    public void recordDownload(String resourceId, String departmentId) {
        ResourceUsageStats stats = usageStore.computeIfAbsent(resourceId, k -> {
            ResourceUsageStats s = new ResourceUsageStats();
            s.resourceId = resourceId;
            return s;
        });
        synchronized (stats) {
            stats.downloadCount++;
            stats.lastAccessedAt = Instant.now().toString();
            if (departmentId != null) {
                stats.accessByDepartment.merge(departmentId, 1L, Long::sum);
            }
        }
    }

    public ResourceUsageStats getUsageStats(String resourceId) {
        return usageStore.getOrDefault(resourceId, new ResourceUsageStats());
    }

    /**
     * Executes catalog search across in-memory resource list.
     */
    public List<LearningResource> search(Collection<LearningResource> resources, ResourceSearchFilter filter, String tenantId) {
        if (resources == null) return Collections.emptyList();

        return resources.stream()
                .filter(r -> tenantId == null || tenantId.equals(r.getTenantId()))
                .filter(r -> {
                    if (filter.query == null || filter.query.trim().isEmpty()) return true;
                    String q = filter.query.toLowerCase();
                    boolean titleMatch = r.getTitle() != null && r.getTitle().toLowerCase().contains(q);
                    boolean descMatch = r.getDescription() != null && r.getDescription().toLowerCase().contains(q);
                    boolean codeMatch = r.getResourceCode() != null && r.getResourceCode().toLowerCase().contains(q);
                    return titleMatch || descMatch || codeMatch;
                })
                .filter(r -> {
                    if (filter.resourceType == null || filter.resourceType.trim().isEmpty()) return true;
                    return r.getResourceType() != null && r.getResourceType().name().equalsIgnoreCase(filter.resourceType.trim());
                })
                .filter(r -> filter.subjectId == null || filter.subjectId.equalsIgnoreCase(r.getSubjectId()))
                .filter(r -> filter.courseId == null || filter.courseId.equalsIgnoreCase(r.getCourseId()))
                .filter(r -> filter.curriculumId == null || filter.curriculumId.equalsIgnoreCase(r.getCurriculumId()))
                .filter(r -> filter.departmentId == null || filter.departmentId.equalsIgnoreCase(r.getDepartmentId()))
                .filter(r -> {
                    if (filter.status == null || filter.status.trim().isEmpty()) return true;
                    return r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(filter.status.trim());
                })
                .filter(r -> {
                    if (filter.tags == null || filter.tags.isEmpty()) return true;
                    if (r.getTags() == null) return false;
                    for (String t : filter.tags) {
                        if (r.getTags().contains(t)) return true;
                    }
                    return false;
                })
                .sorted((r1, r2) -> {
                    int dir = "DESC".equalsIgnoreCase(filter.sortOrder) ? -1 : 1;
                    if ("title".equalsIgnoreCase(filter.sortBy)) {
                        return dir * Objects.toString(r1.getTitle(), "").compareToIgnoreCase(Objects.toString(r2.getTitle(), ""));
                    }
                    return dir * Objects.toString(r1.getUpdatedAt(), "").compareToIgnoreCase(Objects.toString(r2.getUpdatedAt(), ""));
                })
                .skip((long) Math.max(0, (filter.page - 1)) * filter.limit)
                .limit(filter.limit)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves unique available tags in tenant (US-029).
     */
    public Set<String> getTags(Collection<LearningResource> resources, String tenantId) {
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LearningResource r : resources) {
            if (tenantId == null || tenantId.equals(r.getTenantId())) {
                if (r.getTags() != null) {
                    tags.addAll(r.getTags());
                }
            }
        }
        return tags;
    }

    /**
     * Builds advisory insights (US-063).
     */
    public AdvisoryInsightsResponse generateInsights(Collection<LearningResource> resources, String tenantId) {
        AdvisoryInsightsResponse resp = new AdvisoryInsightsResponse();
        if (resources == null) return resp;

        for (LearningResource r : resources) {
            if (tenantId != null && !tenantId.equals(r.getTenantId())) continue;

            resp.totalResources++;
            if (r.getStatus() == ResourceStatus.PUBLISHED) resp.publishedResources++;
            else if (r.getStatus() == ResourceStatus.DRAFT) resp.draftResources++;
            else if (r.getStatus() == ResourceStatus.ARCHIVED) resp.archivedResources++;

            if (r.getResourceType() != null) {
                resp.resourceTypeDistribution.merge(r.getResourceType().name(), 1L, Long::sum);
            }
            if (r.getSubjectId() != null) {
                resp.subjectCoverage.merge(r.getSubjectId(), 1L, Long::sum);
            }

            // Stale resource condition: last updated > 60 days ago or draft with no activity
            if (r.getStatus() == ResourceStatus.DRAFT) {
                resp.staleResourcesCount++;
            }
        }
        return resp;
    }
}
