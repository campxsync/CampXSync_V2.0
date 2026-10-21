package com.campx.gateway.identity;

import java.util.UUID;

/**
 * Service contract for resolving authoritative CampXSync user identity
 * directly from trusted database persistence records.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface IdentityResolver {

    /**
     * Resolves the current identity, tenant context, active business role,
     * and eligibility status for the authenticated user subject UUID.
     *
     * @param userId The validated Supabase Auth user UUID (from JWT 'sub')
     * @return {@link IdentityResolutionResult} containing the resolved identity or failure diagnosis
     */
    IdentityResolutionResult resolveIdentity(UUID userId);
}
