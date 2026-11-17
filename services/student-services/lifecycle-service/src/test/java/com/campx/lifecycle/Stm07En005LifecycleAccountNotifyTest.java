package com.campx.lifecycle;

import com.campx.lifecycle.integration.StudentAccountAccessPort;
import com.campx.lifecycle.security.FailClosedDelegationPort;
import org.junit.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * STM-07-EN-005 — lifecycle status cuts notify student-account access port.
 */
public class Stm07En005LifecycleAccountNotifyTest {

    @Test
    public void stm07En005_suspendNotifiesStudentAccountAccessPort() {
        final AtomicReference<String> seenStatus = new AtomicReference<String>();
        final List<UUID> seenStudents = new ArrayList<UUID>();
        StudentAccountAccessPort recording = new StudentAccountAccessPort() {
            @Override
            public void onAccessCuttingStatus(UUID tenantId, UUID studentId, String newStatus, String correlationId) {
                seenStatus.set(newStatus);
                seenStudents.add(studentId);
            }
        };
        LifecycleDomainService service = new LifecycleDomainService(
                Clock.systemUTC(), null, null, new FailClosedDelegationPort(), recording);
        UUID tenant = UUID.randomUUID();
        UUID campus = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        service.seedStudent(tenant, studentId, "ACTIVE");
        Actor registrar = new Actor(tenant, UUID.randomUUID(), null, campus, "REGISTRAR", "corr-en005");
        service.transitionStatus(registrar, studentId, "SUSPENDED", "EN-005 cut");
        assertEquals("SUSPENDED", seenStatus.get());
        assertTrue(seenStudents.contains(studentId));
    }

    @Test
    public void stm07En005_graduatedDoesNotNotifyAccessCut() {
        final AtomicReference<String> seenStatus = new AtomicReference<String>();
        StudentAccountAccessPort recording = new StudentAccountAccessPort() {
            @Override
            public void onAccessCuttingStatus(UUID tenantId, UUID studentId, String newStatus, String correlationId) {
                seenStatus.set(newStatus);
            }
        };
        LifecycleDomainService service = new LifecycleDomainService(
                Clock.systemUTC(), null, null, new FailClosedDelegationPort(), recording);
        UUID tenant = UUID.randomUUID();
        UUID campus = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        service.seedStudent(tenant, studentId, "ACTIVE");
        Actor registrar = new Actor(tenant, UUID.randomUUID(), null, campus, "REGISTRAR", "corr-en005-g");
        service.transitionStatus(registrar, studentId, "GRADUATED", "complete");
        assertNull(seenStatus.get());
    }
}
