package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.AssessmentNotFoundException;
import com.campx.academic.assessment.model.AssessmentModels.*;

import java.util.*;

/**
 * Read-only query, analytics, and reporting service for ACD-09 (US-013, US-025, US-026, US-027, US-033, US-048, US-050, US-051, US-052, US-053).
 */
public class AssessmentQueryService {

    private final Map<String, AssessmentStructure> structures;
    private final Map<String, AssessmentComponent> components;
    private final Map<String, OutcomeMapping> mappings;
    private final AuditAdapter auditAdapter;

    public AssessmentQueryService(Map<String, AssessmentStructure> structures,
                                  Map<String, AssessmentComponent> components,
                                  Map<String, OutcomeMapping> mappings,
                                  AuditAdapter auditAdapter) {
        this.structures = structures;
        this.components = components;
        this.mappings = mappings;
        this.auditAdapter = auditAdapter;
    }

    public List<AssessmentStructure> queryAssessments(AssessmentQueryFilter filter, String tenantId) {
        List<AssessmentStructure> matched = new ArrayList<>();
        for (AssessmentStructure s : structures.values()) {
            if (tenantId != null && !tenantId.equals(s.getTenantId())) {
                continue;
            }
            if (filter.subjectId != null && !filter.subjectId.equalsIgnoreCase(s.getSubjectId())) {
                continue;
            }
            if (filter.courseId != null && !filter.courseId.equalsIgnoreCase(s.getCourseId())) {
                continue;
            }
            if (filter.termId != null && !filter.termId.equalsIgnoreCase(s.getTermId())) {
                continue;
            }
            if (filter.academicYear != null && !filter.academicYear.equalsIgnoreCase(s.getAcademicYear())) {
                continue;
            }
            if (filter.status != null && !filter.status.equalsIgnoreCase(s.getStatus().name())) {
                continue;
            }
            if (filter.assessmentType != null && !filter.assessmentType.equalsIgnoreCase(s.getAssessmentType().name())) {
                continue;
            }
            matched.add(s);
        }

        // Pagination
        int page = filter.page > 0 ? filter.page : 1;
        int size = filter.size > 0 ? filter.size : 20;
        int fromIndex = (page - 1) * size;
        if (fromIndex >= matched.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, matched.size());
        return matched.subList(fromIndex, toIndex);
    }

    public List<AssessmentComponent> getComponents(String assessmentId) {
        List<AssessmentComponent> list = new ArrayList<>();
        for (AssessmentComponent c : components.values()) {
            if (c.getAssessmentId().equals(assessmentId)) {
                list.add(c);
            }
        }
        // sort by sequenceNo
        list.sort(Comparator.comparingInt(AssessmentComponent::getSequenceNo));
        return list;
    }

    public List<OutcomeMapping> getOutcomeMappings(String assessmentId, String componentId, OutcomeType outcomeType) {
        List<OutcomeMapping> list = new ArrayList<>();
        for (OutcomeMapping m : mappings.values()) {
            if (!m.getAssessmentId().equals(assessmentId)) {
                continue;
            }
            if (componentId != null && !componentId.isEmpty() && !componentId.equals(m.getComponentId())) {
                continue;
            }
            if (outcomeType != null && m.getOutcomeType() != outcomeType) {
                continue;
            }
            list.add(m);
        }
        return list;
    }

    public SubjectAssessmentMapResponse getEffectiveAssessmentForSubject(String tenantId, String subjectId, String academicYear, String termId) {
        AssessmentStructure target = null;
        for (AssessmentStructure s : structures.values()) {
            if (s.getTenantId().equals(tenantId)
                    && s.getSubjectId().equalsIgnoreCase(subjectId)
                    && s.getStatus() == AssessmentStatus.PUBLISHED) {
                if (academicYear != null && !academicYear.equalsIgnoreCase(s.getAcademicYear())) {
                    continue;
                }
                if (termId != null && !termId.equalsIgnoreCase(s.getTermId())) {
                    continue;
                }
                target = s;
                break;
            }
        }
        if (target == null) {
            // Also allow APPROVED status if EXM or coordinator needs approved pre-published view
            for (AssessmentStructure s : structures.values()) {
                if (s.getTenantId().equals(tenantId)
                        && s.getSubjectId().equalsIgnoreCase(subjectId)
                        && s.getStatus() == AssessmentStatus.APPROVED) {
                    target = s;
                    break;
                }
            }
        }
        if (target == null) {
            throw new AssessmentNotFoundException("No effective published assessment structure found for subject: " + subjectId);
        }

        SubjectAssessmentMapResponse resp = new SubjectAssessmentMapResponse();
        resp.structure = target;
        resp.components = getComponents(target.getId());
        resp.mappings = getOutcomeMappings(target.getId(), null, null);
        return resp;
    }

    public AssessmentCoverageAnalytics getCoverageAnalytics(String tenantId) {
        AssessmentCoverageAnalytics a = new AssessmentCoverageAnalytics();
        a.tenantId = tenantId;

        Set<String> uniqueOutcomes = new HashSet<>();
        Set<String> mappedCompIds = new HashSet<>();

        for (AssessmentStructure s : structures.values()) {
            if (!s.getTenantId().equals(tenantId)) continue;
            a.totalAssessments++;
            if (s.getStatus() == AssessmentStatus.PUBLISHED) a.publishedAssessments++;
            if (s.getStatus() == AssessmentStatus.DRAFT) a.draftAssessments++;
        }

        for (AssessmentComponent c : components.values()) {
            if (!c.getTenantId().equals(tenantId)) continue;
            a.totalComponents++;
            String typeName = c.getComponentType() != null ? c.getComponentType().name() : "OTHER";
            a.componentDistribution.put(typeName, a.componentDistribution.getOrDefault(typeName, 0) + 1);
        }

        for (OutcomeMapping m : mappings.values()) {
            if (!m.getTenantId().equals(tenantId)) continue;
            a.totalOutcomeMappings++;
            uniqueOutcomes.add(m.getOutcomeCode());
            if (m.getComponentId() != null && !m.getComponentId().isEmpty()) {
                mappedCompIds.add(m.getComponentId());
            }
            String oType = m.getOutcomeType() != null ? m.getOutcomeType().name() : "OTHER";
            a.outcomeTypeDistribution.put(oType, a.outcomeTypeDistribution.getOrDefault(oType, 0) + 1);
        }

        a.uniqueOutcomesMapped = uniqueOutcomes.size();

        for (AssessmentComponent c : components.values()) {
            if (c.getTenantId().equals(tenantId) && !mappedCompIds.contains(c.getId())) {
                a.unmappedComponents.add(c.getComponentCode());
            }
        }

        return a;
    }

    public AssessmentBalanceInsights getBalanceInsights(String assessmentId) {
        AssessmentStructure s = structures.get(assessmentId);
        if (s == null) {
            throw new AssessmentNotFoundException("Assessment structure not found: " + assessmentId);
        }

        List<AssessmentComponent> comps = getComponents(assessmentId);
        AssessmentBalanceInsights insights = new AssessmentBalanceInsights();
        insights.assessmentId = s.getId();
        insights.assessmentCode = s.getAssessmentCode();
        insights.totalAssessmentsOrComponents = comps.size();

        double continuousWeight = 0.0;
        double examWeight = 0.0;

        for (AssessmentComponent c : comps) {
            if (c.getComponentType() == ComponentType.TEST
                    || c.getComponentType() == ComponentType.ASSIGNMENT
                    || c.getComponentType() == ComponentType.QUIZ
                    || c.getComponentType() == ComponentType.LAB_WORK) {
                continuousWeight += c.getWeightage();
            } else if (c.getComponentType() == ComponentType.MIDTERM
                    || c.getComponentType() == ComponentType.ENDTERM) {
                examWeight += c.getWeightage();
            } else {
                continuousWeight += c.getWeightage();
            }
        }

        insights.continuousWeightage = Math.round(continuousWeight * 100.0) / 100.0;
        insights.examWeightage = Math.round(examWeight * 100.0) / 100.0;
        insights.internalVsExternalRatio = examWeight == 0 ? continuousWeight : Math.round((continuousWeight / examWeight) * 100.0) / 100.0;
        insights.isBalanced = continuousWeight >= 30.0 && continuousWeight <= 60.0;

        if (insights.isBalanced) {
            insights.insights.add("Continuous assessment vs terminal exam balance meets standard academic norms.");
        } else {
            insights.insights.add("Continuous assessment weightage (" + continuousWeight + "%) falls outside optimal 30%-60% recommendation.");
        }

        return insights;
    }

    public String exportAssessments(String tenantId, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder();
            sb.append("id,assessmentCode,assessmentName,subjectId,academicYear,termId,status,totalMarks,totalWeightage\n");
            for (AssessmentStructure s : structures.values()) {
                if (tenantId == null || tenantId.equals(s.getTenantId())) {
                    sb.append(s.getId()).append(",")
                            .append(s.getAssessmentCode()).append(",")
                            .append("\"").append(s.getAssessmentName()).append("\",")
                            .append(s.getSubjectId()).append(",")
                            .append(s.getAcademicYear()).append(",")
                            .append(s.getTermId()).append(",")
                            .append(s.getStatus()).append(",")
                            .append(s.getTotalMarks()).append(",")
                            .append(s.getTotalWeightage()).append("\n");
                }
            }
            return sb.toString();
        } else {
            // JSON export
            StringBuilder sb = new StringBuilder("{\"tenantId\":\"").append(tenantId).append("\",\"assessments\":[");
            int i = 0;
            for (AssessmentStructure s : structures.values()) {
                if (tenantId == null || tenantId.equals(s.getTenantId())) {
                    if (i++ > 0) sb.append(",");
                    sb.append(s.toJson());
                }
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public List<AssessmentHistory> getVersionHistory(String assessmentId) {
        return auditAdapter.getHistoryByAssessment(assessmentId);
    }
}
