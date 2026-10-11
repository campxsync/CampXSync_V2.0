package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.UserGroup;
import com.campx.admin.institute.model.InstituteModels.UserGroupMember;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe fallback repository for {@link UserGroupRepository}.
 */
public class InMemoryUserGroupRepository implements UserGroupRepository {

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, UserGroupMember> members = new ConcurrentHashMap<>();

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public UserGroup createUserGroup(UserSecurityContext context, UserGroup group) {
        checkContext(context);
        if (group == null) {
            throw new MalformedPayloadException("UserGroup payload cannot be null");
        }
        if (group.getCode() == null || group.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("UserGroup code is required");
        }
        if (group.getName() == null || group.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("UserGroup name is required");
        }

        String tenantStr = context.getTenantId().toString();
        String code = group.getCode().trim().toUpperCase(Locale.ROOT);

        for (UserGroup g : groups.values()) {
            if (g.getDeletedAt() == null && tenantStr.equals(g.getTenantId()) && code.equalsIgnoreCase(g.getCode())) {
                throw new MalformedPayloadException("UserGroup code already exists: " + code);
            }
        }

        String id = group.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        UserGroup copy = new UserGroup();
        copy.setId(id);
        copy.setTenantId(tenantStr);
        copy.setCode(code);
        copy.setName(group.getName().trim());
        copy.setDescription(group.getDescription());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(copy.getCreatedAt());
        copy.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        copy.setRowVersion(1);

        groups.put(id, copy);
        return copy;
    }

    @Override
    public Optional<UserGroup> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        UserGroup g = groups.get(id);
        if (g != null && g.getDeletedAt() == null && context.getTenantId().toString().equals(g.getTenantId())) {
            return Optional.of(g);
        }
        return Optional.empty();
    }

    @Override
    public Optional<UserGroup> findByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        String tenantStr = context.getTenantId().toString();
        String targetCode = code.trim();
        for (UserGroup g : groups.values()) {
            if (g.getDeletedAt() == null && tenantStr.equals(g.getTenantId()) && targetCode.equalsIgnoreCase(g.getCode())) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<UserGroup> listUserGroups(UserSecurityContext context) {
        checkContext(context);
        List<UserGroup> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        for (UserGroup g : groups.values()) {
            if (g.getDeletedAt() == null && tenantStr.equals(g.getTenantId())) {
                result.add(g);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public UserGroup updateUserGroup(UserSecurityContext context, UserGroup group) {
        checkContext(context);
        if (group == null || group.getId() == null || group.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Group ID is required for update");
        }

        UserGroup existing = groups.get(group.getId());
        if (existing == null || existing.getDeletedAt() != null || !context.getTenantId().toString().equals(existing.getTenantId())) {
            throw new ResourceNotFoundException("UserGroup", group.getId());
        }

        if (group.getName() != null && !group.getName().trim().isEmpty()) {
            existing.setName(group.getName().trim());
        }
        if (group.getDescription() != null) {
            existing.setDescription(group.getDescription());
        }

        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        existing.setRowVersion(existing.getRowVersion() + 1);

        return existing;
    }

    @Override
    public void deleteUserGroup(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Group ID is required for deletion");
        }

        UserGroup existing = groups.get(id);
        if (existing != null && existing.getDeletedAt() == null && context.getTenantId().toString().equals(existing.getTenantId())) {
            existing.setDeletedAt(System.currentTimeMillis());
            existing.setUpdatedAt(System.currentTimeMillis());
            existing.setUpdatedBy(context.getUserId() != null ? context.getUserId().toString() : null);
        }
    }

    @Override
    public UserGroupMember addMember(UserSecurityContext context, String groupId, String userId) {
        checkContext(context);
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new MalformedPayloadException("groupId is required");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new MalformedPayloadException("userId is required");
        }

        String tenantStr = context.getTenantId().toString();
        String gid = groupId.trim();
        String uid = userId.trim();

        // Check if group exists
        UserGroup group = groups.get(gid);
        if (group == null || group.getDeletedAt() != null || !tenantStr.equals(group.getTenantId())) {
            throw new ResourceNotFoundException("UserGroup", gid);
        }

        // Check existing membership
        for (UserGroupMember m : members.values()) {
            if (tenantStr.equals(m.getTenantId()) && gid.equals(m.getGroupId()) && uid.equals(m.getUserId())) {
                return m;
            }
        }

        String memberId = UUID.randomUUID().toString();
        UserGroupMember member = new UserGroupMember();
        member.setId(memberId);
        member.setTenantId(tenantStr);
        member.setGroupId(gid);
        member.setUserId(uid);
        member.setCreatedAt(System.currentTimeMillis());
        member.setCreatedBy(context.getUserId() != null ? context.getUserId().toString() : null);

        members.put(memberId, member);
        return member;
    }

    @Override
    public List<UserGroupMember> listMembers(UserSecurityContext context, String groupId) {
        checkContext(context);
        if (groupId == null || groupId.trim().isEmpty()) return Collections.emptyList();

        List<UserGroupMember> result = new ArrayList<>();
        String tenantStr = context.getTenantId().toString();
        String gid = groupId.trim();
        for (UserGroupMember m : members.values()) {
            if (tenantStr.equals(m.getTenantId()) && gid.equals(m.getGroupId())) {
                result.add(m);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void removeMember(UserSecurityContext context, String groupId, String userId) {
        checkContext(context);
        if (groupId == null || userId == null) return;

        String tenantStr = context.getTenantId().toString();
        String gid = groupId.trim();
        String uid = userId.trim();

        for (Iterator<Map.Entry<String, UserGroupMember>> it = members.entrySet().iterator(); it.hasNext(); ) {
            UserGroupMember m = it.next().getValue();
            if (tenantStr.equals(m.getTenantId()) && gid.equals(m.getGroupId()) && uid.equals(m.getUserId())) {
                it.remove();
            }
        }
    }
}
