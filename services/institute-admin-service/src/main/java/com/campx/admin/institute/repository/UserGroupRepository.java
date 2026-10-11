package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.UserGroup;
import com.campx.admin.institute.model.InstituteModels.UserGroupMember;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing user groups and group memberships (Item 19: {@code iam.user_groups} & {@code iam.user_group_members}).
 * Enforces tenant boundary isolation and RLS security under {@code iam.perm_all('adm08.*')}.
 */
public interface UserGroupRepository {

    /**
     * Creates a new user group within the caller's tenant.
     *
     * @param context caller security context
     * @param group   user group specification
     * @return persisted user group
     */
    UserGroup createUserGroup(UserSecurityContext context, UserGroup group);

    /**
     * Finds a user group by ID.
     *
     * @param context caller security context
     * @param id      group UUID
     * @return optional containing user group if present and active
     */
    Optional<UserGroup> findById(UserSecurityContext context, String id);

    /**
     * Finds a user group by unique code within tenant.
     *
     * @param context caller security context
     * @param code    group code
     * @return optional containing user group
     */
    Optional<UserGroup> findByCode(UserSecurityContext context, String code);

    /**
     * Lists active user groups for caller's tenant.
     *
     * @param context caller security context
     * @return list of user groups
     */
    List<UserGroup> listUserGroups(UserSecurityContext context);

    /**
     * Updates an existing user group's name and description.
     *
     * @param context caller security context
     * @param group   group containing updates
     * @return updated user group record
     */
    UserGroup updateUserGroup(UserSecurityContext context, UserGroup group);

    /**
     * Soft-deletes a user group.
     *
     * @param context caller security context
     * @param id      group UUID
     */
    void deleteUserGroup(UserSecurityContext context, String id);

    /**
     * Adds a user to the specified group.
     *
     * @param context caller security context
     * @param groupId group UUID
     * @param userId  user profile UUID
     * @return persisted member record
     */
    UserGroupMember addMember(UserSecurityContext context, String groupId, String userId);

    /**
     * Lists all members in the specified group.
     *
     * @param context caller security context
     * @param groupId group UUID
     * @return list of group members
     */
    List<UserGroupMember> listMembers(UserSecurityContext context, String groupId);

    /**
     * Removes a user from the specified group.
     *
     * @param context caller security context
     * @param groupId group UUID
     * @param userId  user profile UUID
     */
    void removeMember(UserSecurityContext context, String groupId, String userId);
}
