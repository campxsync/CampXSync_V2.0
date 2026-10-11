package com.campx.admin.college.repository;

import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link ProgramRepository}.
 * Used as test double and fallback when database persistence is not configured.
 */
public class InMemoryProgramRepository implements ProgramRepository {

    private final Map<String, Program> store = new ConcurrentHashMap<>();

    public InMemoryProgramRepository() {
        seedDefaults();
    }

    private void seedDefaults() {
        Program p1 = new Program();
        p1.setId("PROG_BTECH_CS");
        p1.setProgramCode("BTECH_CS");
        p1.setName("B.Tech Computer Science and Engineering");
        p1.setDepartmentId("DEP_CS");
        p1.setDurationYears(4);
        p1.setVersion(1);
        p1.setPublished(true);
        p1.setStatus("ACTIVE");
        store.put(p1.getId(), p1);
    }

    @Override
    public Program createProgram(UserSecurityContext context, Program program) {
        if (program == null) {
            throw new CollegeMalformedPayloadException("Program payload cannot be null");
        }
        if (program.getProgramCode() == null || program.getProgramCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'programCode' is required");
        }
        if (program.getName() == null || program.getName().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'name' is required");
        }
        if (program.getDepartmentId() == null || program.getDepartmentId().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'departmentId' is required");
        }

        String code = program.getProgramCode().trim().toUpperCase(Locale.ROOT);
        for (Program existing : store.values()) {
            if (code.equalsIgnoreCase(existing.getProgramCode())) {
                throw new CollegeResourceConflictException("Program", "programCode", code);
            }
        }

        if (program.getId() == null || program.getId().trim().isEmpty()) {
            program.setId(UUID.randomUUID().toString());
        }
        program.setProgramCode(code);
        if (program.getStatus() == null) {
            program.setStatus("ACTIVE");
        }
        program.setCreatedAt(System.currentTimeMillis());
        program.setUpdatedAt(program.getCreatedAt());
        program.setRowVersion(1);

        store.put(program.getId(), program);
        return program;
    }

    @Override
    public Program getProgramById(UserSecurityContext context, String programId) {
        if (programId == null) return null;
        return store.get(programId);
    }

    @Override
    public List<Program> listPrograms(UserSecurityContext context, String collegeId, String departmentId) {
        List<Program> list = new ArrayList<>();
        for (Program p : store.values()) {
            if ("DISCONTINUED".equalsIgnoreCase(p.getStatus())) {
                continue;
            }
            if (collegeId != null && p.getCollegeId() != null && !collegeId.equals(p.getCollegeId())) {
                continue;
            }
            if (departmentId != null && p.getDepartmentId() != null && !departmentId.equals(p.getDepartmentId())) {
                continue;
            }
            list.add(p);
        }
        return list;
    }

    @Override
    public Program updateProgram(UserSecurityContext context, Program program) {
        if (program == null || program.getId() == null) {
            throw new CollegeMalformedPayloadException("Program and program ID are required");
        }
        Program existing = store.get(program.getId());
        if (existing == null) {
            throw new CollegeResourceNotFoundException("Program", program.getId());
        }
        existing.setName(program.getName());
        existing.setDurationYears(program.getDurationYears());
        existing.setPublished(program.isPublished());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);
        return existing;
    }

    @Override
    public void deleteProgram(UserSecurityContext context, String programId) {
        if (programId != null) {
            Program existing = store.get(programId);
            if (existing != null) {
                existing.setStatus("DISCONTINUED");
            }
        }
    }
}
