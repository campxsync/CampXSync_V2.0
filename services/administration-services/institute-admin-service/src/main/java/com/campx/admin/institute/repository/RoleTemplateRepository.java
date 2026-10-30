package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing global role templates and their permission assignments (Item 20: {@code iam.role_templates} & {@code iam.role_template_permissions}).
 * Reads are globally accessible to authenticated users, mutations require platform administrator permissions.
 */
public interface RoleTemplateRepository {

    /**
     * Creates a new global role template.
     *
     * @param context  caller security context
     * @param template role template entity
     * @return persisted role template
     */
    RoleTemplate createRoleTemplate(UserSecurityContext context, RoleTemplate template);

    /**
     * Retrieves a role template by ID.
     *
     * @param context caller security context
     * @param id      template UUID
     * @return optional containing role template if found and active
     */
    Optional<RoleTemplate> findById(UserSecurityContext context, String id);

    /**
     * Retrieves a role template by its unique code.
     *
     * @param context caller security context
     * @param code    role template code (e.g. 'DEAN')
     * @return optional containing role template if found and active
     */
    Optional<RoleTemplate> findByCode(UserSecurityContext context, String code);

    /**
     * Lists all active role templates.
     *
     * @param context caller security context
     * @return list of role templates
     */
    List<RoleTemplate> listRoleTemplates(UserSecurityContext context);

    /**
     * Updates an existing role template.
     *
     * @param context  caller security context
     * @param template role template containing updates
     * @return updated role template entity
     */
    RoleTemplate updateRoleTemplate(UserSecurityContext context, RoleTemplate template);

    /**
     * Soft-deletes a role template.
     *
     * @param context caller security context
     * @param id      template UUID
     */
    void deleteRoleTemplate(UserSecurityContext context, String id);

    /**
     * Binds a permission code to a role template.
     *
     * @param context        caller security context
     * @param roleCode       role template code
     * @param permissionCode permission code (e.g. 'acd01.view')
     * @return persisted role template permission binding
     */
    RoleTemplatePermission addPermission(UserSecurityContext context, String roleCode, String permissionCode);

    /**
     * Lists all permission codes attached to a role template.
     *
     * @param context  caller security context
     * @param roleCode role template code
     * @return list of permission bindings
     */
    List<RoleTemplatePermission> listPermissions(UserSecurityContext context, String roleCode);

    /**
     * Removes a permission binding from a role template.
     *
     * @param context        caller security context
     * @param roleCode       role template code
     * @param permissionCode permission code
     */
    void removePermission(UserSecurityContext context, String roleCode, String permissionCode);
}
