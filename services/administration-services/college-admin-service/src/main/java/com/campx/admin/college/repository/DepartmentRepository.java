package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@code core.departments} persistence.
 */
public interface DepartmentRepository {

    /**
     * Persists a new department under an active college and tenant.
     *
     * @param context    authenticated caller security context
     * @param department department entity to persist
     * @return persisted department with generated ID and timestamps
     */
    Department createDepartment(UserSecurityContext context, Department department);

    /**
     * Finds a department by primary identifier within the caller's tenant.
     *
     * @param context authenticated caller security context
     * @param id      department UUID
     * @return optional containing department if found and accessible
     */
    Optional<Department> findById(UserSecurityContext context, String id);

    /**
     * Finds a department by unique alphanumeric code under a college.
     *
     * @param context   authenticated caller security context
     * @param collegeId college UUID
     * @param code      department code (e.g. "CSE")
     * @return optional containing department if found
     */
    Optional<Department> findByCode(UserSecurityContext context, String collegeId, String code);

    /**
     * Lists active departments for the specified college within caller's tenant.
     * If collegeId is null, returns all departments for the tenant.
     *
     * @param context   authenticated caller security context
     * @param collegeId optional college UUID filter
     * @return list of matching departments
     */
    List<Department> listDepartments(UserSecurityContext context, String collegeId);

    /**
     * Updates department fields (e.g. name, status) under caller's tenant.
     *
     * @param context    authenticated caller security context
     * @param department department entity containing updated fields and non-null ID
     * @return updated department entity
     */
    Department updateDepartment(UserSecurityContext context, Department department);

    /**
     * Soft-retires a department by setting status to 'RETIRED'.
     *
     * @param context authenticated caller security context
     * @param id      department UUID
     */
    void retireDepartment(UserSecurityContext context, String id);

    /**
     * Hard or soft deletes a department (primarily for test teardown).
     *
     * @param context authenticated caller security context
     * @param id      department UUID
     */
    void deleteDepartment(UserSecurityContext context, String id);
}
