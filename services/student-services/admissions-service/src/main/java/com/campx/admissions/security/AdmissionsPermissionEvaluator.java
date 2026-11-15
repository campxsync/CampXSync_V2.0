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

    public static final String DELEGATION_APPROVE_KEY = "STM-02.ADMISSIONS.A";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsPermissionEvaluator.class);
    private static volatile DelegationPort delegationPort = new FailClosedDelegationPort();

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

    /**
     * Unit tests only: inject in-memory delegation grants.
     */
    public static void setDelegationPortForTests(DelegationPort port) {
        delegationPort = port == null ? new FailClosedDelegationPort() : port;
    }

  public static void resetDelegationPortForTests() {
        delegationPort = new FailClosedDelegationPort();
    }

    public static boolean isCatalogueRegistrar(UserSecurityContext context) {
        if (context == null || context.getRole() == null) {
            return false;
        }
        String role = context.getRole().trim().toUpperCase();
        if ("R31".equals(role) || "REGISTRAR".equals(role)) {
            return true;
        }
        if (LegacyRegistrarStandin.isEnabled()) {
            return "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
        }
        return false;
    }

    private static boolean isLegacyCollegeOrInstitutionAdmin(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return "COLLEGE_ADMIN".equals(normalized) || "INSTITUTION_ADMIN".equals(normalized);
    }

    public static void requireRegistrarCampusAndDelegation(UserSecurityContext context) {
        if (!isCatalogueRegistrar(context)) {
            throw new AdmissionsSecurityViolationException("Caller lacks catalogue Registrar (R31) authority");
        }
        if (context.getCollegeId() == null) {
            throw new AdmissionsSecurityViolationException("Assigned campus (college_id) is required for Registrar decisions");
        }
        if (!delegationPort.hasActiveDelegation(context.getTenantId(), context.getUserId(), DELEGATION_APPROVE_KEY)) {
            throw new AdmissionsSecurityViolationException(
                    "Approve/reject requires an active IAM delegation for " + DELEGATION_APPROVE_KEY);
        }
    }

    public static void requireDecisionApprovalPermission(UserSecurityContext context) {
        if (isCatalogueRegistrar(context)) {
            requireRegistrarCampusAndDelegation(context);
            return;
        }
        String role = context.getRole();
        if (DECISION_APPROVER_ROLES.contains(role)) {
            if (isLegacyCollegeOrInstitutionAdmin(role) && !LegacyRegistrarStandin.isEnabled()) {
                throw new AdmissionsSecurityViolationException(
                        "College Admin cannot record admission decisions without legacy registrar stand-in");
            }
            return;
        }
        throw new AdmissionsSecurityViolationException("Caller lacks executive authority to record admission decisions");
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

        // D10: same actor cannot verify (review) and approve in one workflow — no role bypass.
        if (reviewerId != null && context.getUserId().equals(reviewerId)) {
            throw new AdmissionsSecurityViolationException(
                    "SAME_ACTOR_VERIFY_APPROVE: Assigned reviewer cannot approve decision for the same application");
        }
    }

    /**
     * D10 SoD using a submitted review row: any prior review by this user blocks decision approval.
     */
    public static void validateSegregationOfDutiesAgainstSubmittedReview(UserSecurityContext context, UUID submittedReviewerId) {
        if (submittedReviewerId != null && context.getUserId().equals(submittedReviewerId)) {
            throw new AdmissionsSecurityViolationException(
                    "SAME_ACTOR_VERIFY_APPROVE: The same actor cannot both review and approve in one workflow");
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
