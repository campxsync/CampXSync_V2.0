package com.campx.admin.institute.service;

import com.campx.admin.institute.model.InstituteModels.Delegation;
import com.campx.admin.institute.repository.InMemoryDelegationRepository;
import com.campx.admin.institute.security.InMemoryRoleCodeLookup;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Unit tests for Path C M1 {@link DelegationAuthorizationService}.
 */
public class DelegationAuthorizationServiceTest {

    private static final UUID TENANT = UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d");
    private static final UUID OTHER_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FROM = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ROLE_R31 = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String ROLE_FACULTY = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    private InMemoryDelegationRepository repo;
    private InMemoryRoleCodeLookup roles;
    private AtomicLong now;
    private DelegationAuthorizationService service;
    private UserSecurityContext ctx;

    @Before
    public void setUp() {
        repo = new InMemoryDelegationRepository();
        repo.putRoleCode(ROLE_R31, "R31");
        repo.putRoleCode(ROLE_FACULTY, "R10");
        roles = new InMemoryRoleCodeLookup();
        roles.put(ROLE_R31, "R31");
        roles.put(ROLE_FACULTY, "R10");
        now = new AtomicLong(1_700_000_000_000L);
        service = new DelegationAuthorizationService(repo, roles, new DelegationAuthorizationService.ClockSource() {
            @Override
            public long nowMillis() {
                return now.get();
            }
        });
        ctx = new UserSecurityContext(SUBJECT, TENANT);
    }

    private Delegation activeR31() {
        Delegation d = new Delegation();
        d.setFromUserId(FROM.toString());
        d.setToUserId(SUBJECT.toString());
        d.setRoleId(ROLE_R31);
        d.setValidFrom(now.get() - 60_000L);
        d.setValidTo(now.get() + 3_600_000L);
        d.setStatus("ACTIVE");
        d.setReason("test");
        return repo.createDelegation(ctx, d);
    }

    @Test
    public void allowsActiveValidDelegation() {
        activeR31();
        DelegationAuthorizationDecision d = service.checkActive(ctx, SUBJECT.toString(), "STM-06.DOCUMENTS.A");
        assertTrue(d.isAllowed());
        assertEquals("ACTIVE_R31_DELEGATION", d.getReason());
    }

    @Test
    public void deniesMissingDelegation() {
        DelegationAuthorizationDecision d = service.checkActive(ctx, SUBJECT.toString(), "STM-06.DOCUMENTS.A");
        assertFalse(d.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", d.getReason());
    }

    @Test
    public void deniesExpiredDelegation() {
        Delegation d = activeR31();
        d.setValidTo(now.get() - 1L);
        repo.updateDelegation(ctx, d);
        // InMemory update may not shrink validTo below from; force by recreate
        repo.revokeDelegation(ctx, d.getId());
        Delegation expired = new Delegation();
        expired.setFromUserId(FROM.toString());
        expired.setToUserId(SUBJECT.toString());
        expired.setRoleId(ROLE_R31);
        expired.setValidFrom(now.get() - 120_000L);
        expired.setValidTo(now.get() - 1L);
        expired.setStatus("ACTIVE");
        // create requires validTo > validFrom — craft then advance clock past validTo
        expired.setValidTo(now.get() + 1_000L);
        Delegation created = repo.createDelegation(ctx, expired);
        now.set(created.getValidTo() + 1L);
        DelegationAuthorizationDecision decision = service.checkActive(ctx, SUBJECT.toString(), "STM-03.ENROLMENT.A");
        assertFalse(decision.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", decision.getReason());
    }

    @Test
    public void deniesRevokedDelegation() {
        Delegation d = activeR31();
        repo.revokeDelegation(ctx, d.getId());
        DelegationAuthorizationDecision decision = service.checkActive(ctx, SUBJECT.toString(), "STM-04.LIFECYCLE.A");
        assertFalse(decision.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", decision.getReason());
    }

    @Test
    public void deniesWrongTenant() {
        activeR31();
        UserSecurityContext other = new UserSecurityContext(SUBJECT, OTHER_TENANT);
        DelegationAuthorizationDecision d = service.checkActive(other, SUBJECT.toString(), "STM-05.GUARDIAN.A");
        assertFalse(d.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", d.getReason());
    }

    @Test
    public void deniesUnauthorizedPermissionKey() {
        activeR31();
        DelegationAuthorizationDecision d = service.checkActive(ctx, SUBJECT.toString(), "ACD.COURSES.A");
        assertFalse(d.isAllowed());
        assertEquals("UNAUTHORIZED_PERMISSION_KEY", d.getReason());
    }

    @Test
    public void deniesNonRegistrarRoleDelegation() {
        Delegation d = new Delegation();
        d.setFromUserId(FROM.toString());
        d.setToUserId(SUBJECT.toString());
        d.setRoleId(ROLE_FACULTY);
        d.setValidFrom(now.get() - 60_000L);
        d.setValidTo(now.get() + 3_600_000L);
        d.setStatus("ACTIVE");
        repo.createDelegation(ctx, d);
        DelegationAuthorizationDecision decision = service.checkActive(ctx, SUBJECT.toString(), "STM-02.ADMISSIONS.A");
        assertFalse(decision.isAllowed());
        assertEquals("NO_ACTIVE_REGISTRAR_DELEGATION", decision.getReason());
    }

    @Test
    public void deniesCallerSubjectMismatch() {
        activeR31();
        UserSecurityContext otherCaller = new UserSecurityContext(FROM, TENANT);
        DelegationAuthorizationDecision d = service.checkActive(otherCaller, SUBJECT.toString(), "STM-06.DOCUMENTS.A");
        assertFalse(d.isAllowed());
        assertEquals("CALLER_SUBJECT_MISMATCH", d.getReason());
    }

    @Test
    public void allowsRegistrarCodeAlias() {
        repo.putRoleCode(ROLE_R31, "REGISTRAR");
        roles.put(ROLE_R31, "REGISTRAR");
        activeR31();
        DelegationAuthorizationDecision d = service.checkActive(ctx, SUBJECT.toString(), "STM-06.DOCUMENTS.A");
        assertTrue(d.isAllowed());
    }
}
