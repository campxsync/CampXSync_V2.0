package com.campx.admin.college.repository;

import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback implementation of {@link DepartmentRepository}.
 */
public class InMemoryDepartmentRepository implements DepartmentRepository {

    private final Map<String, Department> store = new ConcurrentHashMap<>();

    @Override
    public Department createDepartment(UserSecurityContext context, Department department) {
        if (department.getDepartmentCode() == null || department.getDepartmentCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Department code is required");
        }
        for (Department existing : store.values()) {
            if (existing.getDepartmentCode().equalsIgnoreCase(department.getDepartmentCode())
                    && Objects.equals(existing.getCollegeId(), department.getCollegeId())) {
                throw new CollegeResourceConflictException("Department", "departmentCode", department.getDepartmentCode());
            }
        }
        if (department.getId() == null || department.getId().trim().isEmpty()) {
            department.setId(UUID.randomUUID().toString());
        }
        if (department.getStatus() == null) {
            department.setStatus("ACTIVE");
        }
        if (context != null && context.getTenantId() != null) {
            department.setTenantId(context.getTenantId().toString());
        }
        store.put(department.getId(), department);
        return department;
    }

    @Override
    public Optional<Department> findById(UserSecurityContext context, String id) {
        Department dep = store.get(id);
        if (dep != null && context != null && context.getTenantId() != null
                && dep.getTenantId() != null && !context.getTenantId().toString().equals(dep.getTenantId())) {
            return Optional.empty(); // tenant isolation
        }
        return Optional.ofNullable(dep);
    }

    @Override
    public Optional<Department> findByCode(UserSecurityContext context, String collegeId, String code) {
        for (Department d : store.values()) {
            if (d.getDepartmentCode().equalsIgnoreCase(code)
                    && (collegeId == null || collegeId.equals(d.getCollegeId()))) {
                if (context != null && context.getTenantId() != null
                        && d.getTenantId() != null && !context.getTenantId().toString().equals(d.getTenantId())) {
                    continue; // tenant isolation
                }
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Department> listDepartments(UserSecurityContext context, String collegeId) {
        List<Department> result = new ArrayList<>();
        for (Department d : store.values()) {
            if ("RETIRED".equals(d.getStatus())) continue;
            if (context != null && context.getTenantId() != null
                    && d.getTenantId() != null && !context.getTenantId().toString().equals(d.getTenantId())) {
                continue; // tenant isolation
            }
            if (collegeId != null && !collegeId.trim().isEmpty() && !collegeId.equals(d.getCollegeId())) {
                continue;
            }
            result.add(d);
        }
        return result;
    }

    @Override
    public Department updateDepartment(UserSecurityContext context, Department dep) {
        if (dep == null || dep.getId() == null) return null;
        Department existing = store.get(dep.getId());
        if (existing == null) {
            throw new com.campx.admin.college.exception.CollegeResourceNotFoundException("Department", dep.getId());
        }
        if (dep.getName() != null && !dep.getName().trim().isEmpty()) {
            existing.setName(dep.getName().trim());
        }
        if (dep.getStatus() != null && !dep.getStatus().trim().isEmpty()) {
            existing.setStatus(dep.getStatus().trim());
        }
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);
        return existing;
    }

    @Override
    public void retireDepartment(UserSecurityContext context, String id) {
        Department d = store.get(id);
        if (d != null) {
            d.setStatus("RETIRED");
        }
    }

    @Override
    public void deleteDepartment(UserSecurityContext context, String id) {
        store.remove(id);
    }
}
