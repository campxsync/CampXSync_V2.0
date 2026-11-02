package com.campx.admissions.security;

import com.campx.admissions.exception.AdmissionsSecurityViolationException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.*;

/**
 * Enforces Hybrid RBAC + ABAC authorization and Segregation of Duties (SoD)
 * for STM-02 Admissions & Application Management Service.
 */
public class AdmissionsPermissionEvaluator {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsPermissionEvaluator.class);

    // Administrative roles authorized to configure intakes and manage admissions operations
    private static final Set<String> ADMISSIONS_ADMIN_ROLES = new HashSet<>(Arrays.asList(
            "R01", "R02", "R03", "R04", "SUPER_ADMIN", "SYSTEM_ADMIN", "INSTITUTION_ADMIN", "COLLEGE_ADMIN",
            "ADMISSIONS_ADMINISTRATOR", "ADMISSIONS_MANAGER", "ADMISSIONS_OFFICER", "R32",
            "Super Admin", "System Admin", "Institution Admin", "College Admin",
            "Admissions Administrator", "Admissions Manager", "Admissions Officer"
    ));

    // Decision approver roles authorized to commit binding admission decisions
    private static final Set<String> DECISION_APPROVER_ROLES = new HashSet<>(Arrays.asList(
            "R01", "R03", "R04", "R05", "R07", "PRINCIPAL", "DEAN", "COLLEGE_ADMIN",
            "AUTHORIZED_ADMISSION_APPROVER", "ADMISSIONS_APPROVER", "R33",
            "Principal", "Dean", "College Admin",
            "Authorized Admission Approver", "Admissions Approver"
    ));

    // Document verifier roles
    private static final Set<String> DOCUMENT_VERIFIER_ROLES = new HashSet<>(Arrays.asList(
            "R01", "R03", "R04", "COLLEGE_ADMIN", "ADMISSIONS_OFFICER", "ADMISSIONS_STAFF",
            "AUTHORIZED_DOCUMENT_VERIFIER", "R32",
            "College Admin", "Admissions Officer", "Admissions Staff",
            "Authorized Document Verifier"
    ));

    // Department reviewer roles
    private static final Set<String> REVIEWER_ROLES = new HashSet<>(Arrays.asList(
            "R07", "R08", "R10", "DEAN", "HOD", "FACULTY", "DEPARTMENT_REVIEWER",
            "FACULTY_REVIEWER", "ASSIGNED_DEPARTMENT_REVIEWER",
            "Dean", "HOD", "Faculty", "Department Reviewer",
            "Assigned Department Reviewer"
    ));

    // Audit and reporting roles
    private static final Set<String> AUDIT_ROLES = new HashSet<>(Arrays.asList(
            "R01", "R02", "R04", "R28", "AUDITOR", "SUPER_ADMIN",
            "Auditor", "Super Admin"
    ));

    private static final Set<String> REPORTING_ROLES = new HashSet<>(Arrays.asList(
            "R01", "R03", "R04", "R05", "R29", "REPORTING_USER", "REPORTING_ANALYTICS_USER",
            "ADMISSIONS_ADMINISTRATOR", "ADMISSIONS_MANAGER",
            "Reporting User", "Reporting/Analytics User",
            "Admissions Administrator", "Admissions Manager"
    ));

    public static void requireIntakeConfigurationPermission(UserSecurityContext context) {
        if (!isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller lacks permission to configure admission intakes");
        }
    }

    public static void requireDocumentVerificationPermission(UserSecurityContext context) {
        if (!DOCUMENT_VERIFIER_ROLES.contains(context.getRole()) && !isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller lacks permission to verify application documents");
        }
    }

    public static void requireReviewSubmissionPermission(UserSecurityContext context, UUID assignedReviewerId, UUID applicationDeptId) {
        String role = context.getRole();
        if (!REVIEWER_ROLES.contains(role) && !isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller role '" + role + "' is not authorized as a department reviewer");
        }

        // If explicitly assigned, must match reviewer identity
        if (assignedReviewerId != null && !assignedReviewerId.equals(context.getUserId()) && !isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller is not the assigned reviewer for this application");
        }

        // Department scope match if assigned
        if (applicationDeptId != null && context.getDepartmentId() != null && !isAdmissionsAdmin(context)) {
            if (!applicationDeptId.equals(context.getDepartmentId())) {
                throw new AdmissionsSecurityViolationException("Reviewer department does not match application department scope");
            }
        }
    }

    public static void requireDecisionApprovalPermission(UserSecurityContext context) {
        if (!DECISION_APPROVER_ROLES.contains(context.getRole())) {
            throw new AdmissionsSecurityViolationException("Caller lacks executive authority to record admission decisions");
        }
    }

    public static void requireAuditLogReadPermission(UserSecurityContext context) {
        if (!AUDIT_ROLES.contains(context.getRole()) && !isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller lacks permission to view admissions audit trails");
        }
    }

    public static void requireReportingPermission(UserSecurityContext context) {
        if (!REPORTING_ROLES.contains(context.getRole()) && !isAdmissionsAdmin(context)) {
            throw new AdmissionsSecurityViolationException("Caller lacks permission to view admissions reports and funnels");
        }
    }

    public static void requireApplicationOwnershipOrStaff(UserSecurityContext context, UUID applicantPersonId) {
        if (applicantPersonId == null) {
            throw new AdmissionsSecurityViolationException("Invalid application: applicant person ID is null");
        }

        if (context.getUserId().equals(applicantPersonId)) {
            return; // Valid self-service access
        }

        if (isAdmissionsAdmin(context) || REVIEWER_ROLES.contains(context.getRole()) || DECISION_APPROVER_ROLES.contains(context.getRole())) {
            return; // Authorized staff/reviewer access
        }

        throw new AdmissionsSecurityViolationException("Caller is not authorized to access this application (Ownership violation)");
    }

    public static void validateSegregationOfDutiesOnDecision(UserSecurityContext context, UUID applicantPersonId, UUID reviewerId) {
        // Submitter cannot approve own application
        if (context.getUserId().equals(applicantPersonId)) {
            throw new AdmissionsSecurityViolationException("Segregation of duties violation: Applicant cannot approve their own application");
        }

        // Reviewer who scored cannot be the decision approver (Segregation of Duties)
        if (reviewerId != null && context.getUserId().equals(reviewerId) && !"Super Admin".equalsIgnoreCase(context.getRole()) && !"R01".equalsIgnoreCase(context.getRole())) {
            throw new AdmissionsSecurityViolationException("Segregation of duties violation: Assigned reviewer cannot approve decision for the same application");
        }
    }

    public static boolean isAdmissionsAdmin(UserSecurityContext context) {
        return ADMISSIONS_ADMIN_ROLES.contains(context.getRole());
    }

    public static boolean isApplicant(UserSecurityContext context) {
        String r = context.getRole();
        return "Applicant".equalsIgnoreCase(r) || "R31".equalsIgnoreCase(r) || "R25".equalsIgnoreCase(r);
    }
}
