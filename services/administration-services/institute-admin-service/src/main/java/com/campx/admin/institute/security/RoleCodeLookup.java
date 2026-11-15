package com.campx.admin.institute.security;

import java.util.Optional;

/**
 * Resolves {@code iam.roles.id} to catalogue role code (e.g. {@code R31}, {@code REGISTRAR}).
 * Used by Path C M1 delegation checks inside institute-admin only.
 */
public interface RoleCodeLookup {

    /**
     * @param context tenant-scoped caller context
     * @param roleId  role UUID string
     * @return role code when visible under RLS; empty when missing or unauthorized
     */
    Optional<String> findCodeByRoleId(UserSecurityContext context, String roleId);
}
