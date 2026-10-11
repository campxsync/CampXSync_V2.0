package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Campus;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryCampusRepositoryTest {

    private InMemoryCampusRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;
    private UUID collegeId;

    @Before
    public void setUp() {
        repository = new InMemoryCampusRepository();
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveCampus() {
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode("MAIN_CAMPUS");
        campus.setName("North Main Campus");
        campus.setPrimary(true);

        Campus created = repository.createCampus(tenantContext, campus);
        assertNotNull(created.getId());
        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals("MAIN_CAMPUS", created.getCode());
        assertTrue(created.isPrimary());

        Optional<Campus> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals("North Main Campus", byId.get().getName());

        Optional<Campus> byCode = repository.findByCode(tenantContext, collegeId.toString(), "main_campus");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<Campus> list = repository.listCampuses(tenantContext, collegeId.toString());
        assertEquals(1, list.size());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateCodeInCollegeThrows() {
        Campus c1 = new Campus();
        c1.setCollegeId(collegeId.toString());
        c1.setCode("CAMPUS_DUP");
        c1.setName("First");
        repository.createCampus(tenantContext, c1);

        Campus c2 = new Campus();
        c2.setCollegeId(collegeId.toString());
        c2.setCode("campus_dup");
        c2.setName("Second");
        repository.createCampus(tenantContext, c2);
    }

    @Test
    public void testUpdateCampus() {
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode("CAMPUS_UPD");
        campus.setName("Original Name");
        Campus created = repository.createCampus(tenantContext, campus);

        Campus update = new Campus();
        update.setId(created.getId());
        update.setName("Updated Campus Name");
        update.setPrimary(true);
        update.setStatus("SUSPENDED");

        Campus updated = repository.updateCampus(tenantContext, update);
        assertEquals("Updated Campus Name", updated.getName());
        assertTrue(updated.isPrimary());
        assertEquals("SUSPENDED", updated.getStatus());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testDeleteCampus() {
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode("CAMPUS_DEL");
        campus.setName("To Delete");
        Campus created = repository.createCampus(tenantContext, campus);

        repository.deleteCampus(tenantContext, created.getId());
        Optional<Campus> byId = repository.findById(tenantContext, created.getId());
        assertFalse(byId.isPresent());
    }

    @Test
    public void testTenantIsolation() {
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode("CAMPUS_ISO");
        campus.setName("Isolated Campus");
        Campus created = repository.createCampus(tenantContext, campus);

        Optional<Campus> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse(cross.isPresent());

        List<Campus> otherList = repository.listCampuses(otherTenantContext, collegeId.toString());
        assertTrue(otherList.isEmpty());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listCampuses(null, null);
    }
}
