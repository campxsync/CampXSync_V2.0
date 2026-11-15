package com.campx.admin.institute.security;

import com.campx.admin.institute.exception.SecurityViolationException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory role-id → code map for unit tests (no database).
 */
public final class InMemoryRoleCodeLookup implements RoleCodeLookup {

    private final Map<String, String> codesByRoleId = new ConcurrentHashMap<String, String>();

    public void put(String roleId, String code) {
        if (roleId == null || roleId.trim().isEmpty() || code == null || code.trim().isEmpty()) {
            return;
        }
        codesByRoleId.put(roleId.trim(), code.trim().toUpperCase(Locale.ROOT));
    }

    public void clear() {
        codesByRoleId.clear();
    }

    @Override
    public Optional<String> findCodeByRoleId(UserSecurityContext context, String roleId) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        if (roleId == null || roleId.trim().isEmpty()) {
            return Optional.empty();
        }
        String code = codesByRoleId.get(roleId.trim());
        if (code != null) {
            return Optional.of(code);
        }
        // Convenience for tests that store the catalogue code directly as roleId.
        String asCode = roleId.trim().toUpperCase(Locale.ROOT);
        if ("R31".equals(asCode) || "REGISTRAR".equals(asCode)) {
            return Optional.of(asCode);
        }
        return Optional.empty();
    }
}
