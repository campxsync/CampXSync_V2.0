package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.LookupType;
import com.campx.admin.institute.model.InstituteModels.LookupValue;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryLookupRepositoryTest {

    private InMemoryLookupRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryLookupRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveLookupType() {
        LookupType type = new LookupType();
        type.setCode("GENDER");
        type.setName("Gender Categories");
        type.setDescription("Standard gender classifications");

        LookupType created = repository.createType(tenantContext, type);
        assertNotNull(created.getId());
        assertEquals("GENDER", created.getCode());
        assertEquals("Gender Categories", created.getName());

        Optional<LookupType> byId = repository.findTypeById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<LookupType> byCode = repository.findTypeByCode(tenantContext, "gender");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<LookupType> list = repository.listTypes(tenantContext);
        assertEquals(1, list.size());

        // Isolation
        assertFalse(repository.findTypeById(otherTenantContext, created.getId()).isPresent());
        assertFalse(repository.findTypeByCode(otherTenantContext, "GENDER").isPresent());
    }

    @Test
    public void testLookupValuesLifecycle() {
        LookupType type = new LookupType();
        type.setCode("SEMESTER");
        type.setName("Semester Types");
        LookupType createdType = repository.createType(tenantContext, type);

        LookupValue v1 = new LookupValue();
        v1.setLookupTypeId(createdType.getId());
        v1.setCode("ODD");
        v1.setLabel("Odd Semester (Autumn)");
        v1.setSortOrder(1);
        v1.setActive(true);
        v1.setAttrs("{\"season\": \"fall\"}");
        LookupValue saved1 = repository.createValue(tenantContext, v1);
        assertNotNull(saved1.getId());
        assertEquals("ODD", saved1.getCode());

        LookupValue v2 = new LookupValue();
        v2.setLookupTypeId(createdType.getId());
        v2.setCode("EVEN");
        v2.setLabel("Even Semester (Spring)");
        v2.setSortOrder(2);
        repository.createValue(tenantContext, v2);

        List<LookupValue> values = repository.listValuesByType(tenantContext, createdType.getId());
        assertEquals(2, values.size());
        assertEquals("ODD", values.get(0).getCode());
        assertEquals("EVEN", values.get(1).getCode());

        Optional<LookupValue> byCode = repository.findValueByCode(tenantContext, createdType.getId(), "odd");
        assertTrue(byCode.isPresent());
        assertEquals(saved1.getId(), byCode.get().getId());

        saved1.setLabel("Updated Odd Term");
        LookupValue updated = repository.updateValue(tenantContext, saved1);
        assertEquals("Updated Odd Term", updated.getLabel());
        assertEquals(2, updated.getRowVersion());

        repository.deleteValue(tenantContext, saved1.getId());
        assertFalse(repository.findValueById(tenantContext, saved1.getId()).isPresent());
    }

    @Test
    public void testCascadeDeleteOnType() {
        LookupType type = new LookupType();
        type.setCode("TITLE");
        type.setName("Academic Titles");
        LookupType createdType = repository.createType(tenantContext, type);

        LookupValue v1 = new LookupValue();
        v1.setLookupTypeId(createdType.getId());
        v1.setCode("DR");
        v1.setLabel("Doctor");
        repository.createValue(tenantContext, v1);

        repository.deleteType(tenantContext, createdType.getId());
        assertFalse(repository.findTypeById(tenantContext, createdType.getId()).isPresent());

        List<LookupValue> remaining = repository.listValuesByType(tenantContext, createdType.getId());
        assertTrue(remaining.isEmpty());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateTypeCodeRejection() {
        LookupType t1 = new LookupType();
        t1.setCode("DUP");
        t1.setName("Name 1");
        repository.createType(tenantContext, t1);

        LookupType t2 = new LookupType();
        t2.setCode("DUP");
        t2.setName("Name 2");
        repository.createType(tenantContext, t2);
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContext() {
        repository.createType(null, new LookupType());
    }
}
