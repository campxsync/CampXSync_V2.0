package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;

/**
 * Data access abstraction for Academic Program entities.
 * Supports dual-mode execution (PostgreSQL RLS transactional persistence vs. in-memory test double).
 */
public interface ProgramRepository {

    /**
     * Persists a new program in the operational store under the caller's tenant boundary.
     *
     * @param context caller security context
     * @param program program definition to persist
     * @return persisted program entity
     */
    Program createProgram(UserSecurityContext context, Program program);

    /**
     * Retrieves a program by identifier within the caller's tenant boundary.
     *
     * @param context   caller security context
     * @param programId primary program UUID
     * @return program entity if found, null otherwise
     */
    Program getProgramById(UserSecurityContext context, String programId);

    /**
     * Lists programs belonging to the caller's tenant, with optional college or department filters.
     *
     * @param context      caller security context
     * @param collegeId    optional college filter
     * @param departmentId optional department filter
     * @return list of programs
     */
    List<Program> listPrograms(UserSecurityContext context, String collegeId, String departmentId);

    /**
     * Updates an existing program under optimistic locking.
     *
     * @param context caller security context
     * @param program updated program entity
     * @return updated program entity
     */
    Program updateProgram(UserSecurityContext context, Program program);

    /**
     * Soft-deletes / retires a program.
     *
     * @param context   caller security context
     * @param programId program identifier
     */
    void deleteProgram(UserSecurityContext context, String programId);
}
