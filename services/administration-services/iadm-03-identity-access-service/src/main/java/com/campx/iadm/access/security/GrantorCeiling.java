package com.campx.iadm.access.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * RBAC v3.0 §21.1 grantor ceilings encoded as data (IADM-03-US-003).
 * Fail closed on missing, expired or ambiguous authority.
 */
public final class GrantorCeiling {

    private static final Set<String> R01_MAY_ASSIGN = setOf("SYSTEM_ADMIN", "R02", "INSTITUTION_ADMIN", "R03");
    private static final Set<String> R01_MAY_NOT = setOf("SUPER_ADMIN", "R01");
    private static final Set<String> R03_MAY_NOT = setOf(
            "SUPER_ADMIN", "R01", "SYSTEM_ADMIN", "R02", "INSTITUTION_ADMIN", "R03",
            "STUDENT", "R25", "PARENT", "R26", "APPLICANT", "R32", "API_CLIENT", "R27");
    private static final Set<String> R04_PROHIBITED = setOf(
            "SUPER_ADMIN", "R01", "SYSTEM_ADMIN", "R02", "INSTITUTION_ADMIN", "R03",
            "COLLEGE_ADMIN", "R04", "PRINCIPAL", "R05", "VICE_PRINCIPAL", "R06", "DEAN", "R07",
            "API_CLIENT", "R27", "AUDITOR", "R28", "EXTERNAL_VERIFIER", "R37",
            "IAM_OPERATOR", "R38");
    private static final Set<String> R38_ONLY = setOf("STUDENT", "R25", "PARENT", "R26");

    private GrantorCeiling() {
    }

    /**
     * @return null if allowed; otherwise a denial reason (fail closed).
     */
    public static String denyReason(String grantorPersona, String targetRoleCode, boolean selfGrant) {
        if (grantorPersona == null || targetRoleCode == null) {
            return "Missing grantor or target role — fail closed.";
        }
        if (selfGrant) {
            return "Nobody may edit their own grants.";
        }
        String g = AccessPermissionMatrix.normalizePersona(grantorPersona);
        String t = targetRoleCode.trim().toUpperCase(Locale.ROOT);

        if ("R27".equals(g)) {
            return "API Client (R27) has no grant authority in IADM-03.";
        }
        if ("R28".equals(g)) {
            return "Auditor access is read-only.";
        }
        if ("R04".equals(g)) {
            return "College Admin (R04) is request-only in IADM-03; direct grants are unavailable.";
        }
        if ("R02".equals(g)) {
            if (isTenantBusinessRole(t) || R01_MAY_ASSIGN.contains(t) || "COLLEGE_ADMIN".equals(t) || "R04".equals(t)) {
                return "System Admin (R02) may assign platform technical service identities only.";
            }
            // Platform technical identities (SERVICE / custom) allowed; R03/R04 denied above
            if ("INSTITUTION_ADMIN".equals(t) || "R03".equals(t) || "COLLEGE_ADMIN".equals(t) || "R04".equals(t)) {
                return "System Admin (R02) cannot assign Institution or College Admin.";
            }
            return null;
        }
        if ("R01".equals(g)) {
            if (R01_MAY_NOT.contains(t)) {
                return "Super Admin (R01) cannot assign additional Super Admin without separate approval workflow.";
            }
            if ("API_CLIENT".equals(t) || "R27".equals(t)) {
                return "API Client (R27) creation/elevation is parked; denial only.";
            }
            if (!R01_MAY_ASSIGN.contains(t)) {
                return "Super Admin (R01) assigns only System Admin (R02) and Institution Admin (R03) in this design.";
            }
            return null;
        }
        if ("R03".equals(g)) {
            if (R03_MAY_NOT.contains(t)) {
                if ("STUDENT".equals(t) || "R25".equals(t) || "PARENT".equals(t) || "R26".equals(t)) {
                    return "Student and Guardian provisioning must use STM-07 / R38.";
                }
                if ("APPLICANT".equals(t) || "R32".equals(t)) {
                    return "Applicant (R32) self-registers through STM-02.";
                }
                if ("API_CLIENT".equals(t) || "R27".equals(t)) {
                    return "API Client (R27) creation/elevation is parked; denial only.";
                }
                return "Institution Admin (R03) cannot assign R01, R02 or R03.";
            }
            return null;
        }
        // Provisioning operator (not an IADM-03 desk actor normally)
        if ("IAM_OPERATOR".equals(g) || "R38".equals(g)) {
            if (!R38_ONLY.contains(t)) {
                return "Provisioning / IAM Operator (R38) may assign only Student (R25) and Parent (R26).";
            }
            return null;
        }
        return "Grantor authority is unknown or ambiguous — fail closed.";
    }

    public static boolean isProhibitedForCollegeAdminTarget(String targetRoleCode) {
        if (targetRoleCode == null) {
            return true;
        }
        return R04_PROHIBITED.contains(targetRoleCode.trim().toUpperCase(Locale.ROOT));
    }

    private static boolean isTenantBusinessRole(String t) {
        return !("SERVICE".equals(t) || "TECHNICAL".equals(t) || t.startsWith("SVC-"));
    }

    private static Set<String> setOf(String... keys) {
        Set<String> s = new HashSet<String>();
        for (String k : keys) {
            s.add(k);
        }
        return Collections.unmodifiableSet(s);
    }
}
