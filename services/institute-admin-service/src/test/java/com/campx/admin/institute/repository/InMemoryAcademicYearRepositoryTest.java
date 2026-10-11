package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.AcademicYear;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryAcademicYearRepositoryTest {

    private InMemoryAcademicYearRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryAcademicYearRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveAcademicYear() {
        AcademicYear year = new AcademicYear();
        year.setCode("AY-2026-2027");
        year.setStartDate(1767225600000L); // 2026-01-01
        year.setEndDate(1798761600000L);   // 2026-12-31
        year.setCurrent(true);

        AcademicYear created = repository.createAcademicYear(tenantContext, year);
        assertNotNull(created.getId());
        assertEquals("AY-2026-2027", created.getCode());
        assertTrue(created.isCurrent());

        Optional<AcademicYear> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<AcademicYear> byCode = repository.findByCode(tenantContext, "ay-2026-2027");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        Optional<AcademicYear> current = repository.findCurrent(tenantContext);
        assertTrue(current.isPresent());
        assertEquals(created.getId(), current.get().getId());

        List<AcademicYear> list = repository.listAcademicYears(tenantContext);
        assertEquals(1, list.size());
    }

    @Test
    public void testSetCurrentSwitchesFlag() {
        AcademicYear y1 = new AcademicYear();
        y1.setCode("AY-2025");
        y1.setStartDate(1735689600000L);
        y1.setEndDate(1767225600000L);
        y1.setCurrent(true);
        AcademicYear created1 = repository.createAcademicYear(tenantContext, y1);

        AcademicYear y2 = new AcademicYear();
        y2.setCode("AY-2026");
        y2.setStartDate(1767225600000L);
        y2.setEndDate(1798761600000L);
        y2.setCurrent(false);
        AcademicYear created2 = repository.createAcademicYear(tenantContext, y2);

        assertEquals(created1.getId(), repository.findCurrent(tenantContext).get().getId());

        repository.setCurrent(tenantContext, created2.getId());

        assertEquals(created2.getId(), repository.findCurrent(tenantContext).get().getId());
        assertFalse(repository.findById(tenantContext, created1.getId()).get().isCurrent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateCodeThrows() {
        AcademicYear y1 = new AcademicYear();
        y1.setCode("AY-DUP");
        y1.setStartDate(1000L);
        y1.setEndDate(2000L);
        repository.createAcademicYear(tenantContext, y1);

        AcademicYear y2 = new AcademicYear();
        y2.setCode("ay-dup");
        y2.setStartDate(1000L);
        y2.setEndDate(2000L);
        repository.createAcademicYear(tenantContext, y2);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testInvalidDatesThrow() {
        AcademicYear y = new AcademicYear();
        y.setCode("AY-INV");
        y.setStartDate(2000L);
        y.setEndDate(1000L);
        repository.createAcademicYear(tenantContext, y);
    }

    @Test
    public void testUpdateAcademicYear() {
        AcademicYear y = new AcademicYear();
        y.setCode("AY-ORIG");
        y.setStartDate(1000L);
        y.setEndDate(2000L);
        AcademicYear created = repository.createAcademicYear(tenantContext, y);

        AcademicYear update = new AcademicYear();
        update.setId(created.getId());
        update.setCode("AY-MODIFIED");
        update.setStartDate(1500L);
        update.setEndDate(2500L);

        AcademicYear updated = repository.updateAcademicYear(tenantContext, update);
        assertEquals("AY-MODIFIED", updated.getCode());
        assertEquals(1500L, updated.getStartDate());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testDeleteAcademicYear() {
        AcademicYear y = new AcademicYear();
        y.setCode("AY-DEL");
        y.setStartDate(1000L);
        y.setEndDate(2000L);
        AcademicYear created = repository.createAcademicYear(tenantContext, y);

        repository.deleteAcademicYear(tenantContext, created.getId());
        Optional<AcademicYear> byId = repository.findById(tenantContext, created.getId());
        assertFalse(byId.isPresent());
    }

    @Test
    public void testTenantIsolation() {
        AcademicYear y = new AcademicYear();
        y.setCode("AY-ISO");
        y.setStartDate(1000L);
        y.setEndDate(2000L);
        AcademicYear created = repository.createAcademicYear(tenantContext, y);

        Optional<AcademicYear> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse(cross.isPresent());

        List<AcademicYear> otherList = repository.listAcademicYears(otherTenantContext);
        assertTrue(otherList.isEmpty());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listAcademicYears(null);
    }
}
