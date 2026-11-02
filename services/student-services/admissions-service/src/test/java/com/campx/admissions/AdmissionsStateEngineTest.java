package com.campx.admissions;

import com.campx.admissions.exception.AdmissionsLifecycleException;
import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.model.ApplicationStatusHistoryEntity;
import com.campx.admissions.repository.InMemoryStatusHistoryRepository;
import com.campx.admissions.repository.StatusHistoryRepository;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.admissions.service.AdmissionsStateEngine;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class AdmissionsStateEngineTest {

    private AdmissionsStateEngine stateEngine;
    private StatusHistoryRepository historyRepository;
    private UserSecurityContext context;
    private UUID tenantId;
    private UUID applicationId;
    private UUID actorId;

    @Before
    public void setUp() {
        historyRepository = new InMemoryStatusHistoryRepository();
        stateEngine = new AdmissionsStateEngine(historyRepository, null);
        tenantId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        context = new UserSecurityContext(actorId, tenantId, null, null, "ADMISSIONS_OFFICER", false);
    }

    @Test
    public void testValidLifecycleProgression() {
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setApplicationNumber("APP-2026-0001");
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRowVersion(0);

        // DRAFT -> SUBMITTED
        stateEngine.transition(context, app, ApplicationStatus.SUBMITTED, "SUBMIT", "Applicant submitted", "c-1");
        assertEquals(ApplicationStatus.SUBMITTED, app.getStatus());

        // SUBMITTED -> ELIGIBLE
        stateEngine.transition(context, app, ApplicationStatus.ELIGIBLE, "EVALUATE_ELIGIBILITY", "Automated criteria passed", "c-2");
        assertEquals(ApplicationStatus.ELIGIBLE, app.getStatus());

        // ELIGIBLE -> UNDER_REVIEW
        stateEngine.transition(context, app, ApplicationStatus.UNDER_REVIEW, "ASSIGN_REVIEWER", "Assigned to committee", "c-3");
        assertEquals(ApplicationStatus.UNDER_REVIEW, app.getStatus());

        // UNDER_REVIEW -> OFFERED
        stateEngine.transition(context, app, ApplicationStatus.OFFERED, "ISSUE_OFFER", "Committee approved and offer generated", "c-4");
        assertEquals(ApplicationStatus.OFFERED, app.getStatus());

        // OFFERED -> ACCEPTED
        stateEngine.transition(context, app, ApplicationStatus.ACCEPTED, "ACCEPT_OFFER", "Applicant accepted offer", "c-5");
        assertEquals(ApplicationStatus.ACCEPTED, app.getStatus());

        // ACCEPTED -> HANDED_OVER
        stateEngine.transition(context, app, ApplicationStatus.HANDED_OVER, "COMPLETE_HANDOVER", "Matriculated into STM-01", "c-6");
        assertEquals(ApplicationStatus.HANDED_OVER, app.getStatus());

        // Verify status history audit records (chronological order)
        List<ApplicationStatusHistoryEntity> history = historyRepository.findByApplication(tenantId, applicationId);
        assertEquals(6, history.size());
        assertEquals(ApplicationStatus.DRAFT, history.get(0).getFromStatus());
        assertEquals(ApplicationStatus.SUBMITTED, history.get(0).getToStatus());
        assertEquals(ApplicationStatus.ACCEPTED, history.get(5).getFromStatus());
        assertEquals(ApplicationStatus.HANDED_OVER, history.get(5).getToStatus());
    }

    @Test(expected = AdmissionsLifecycleException.class)
    public void testInvalidTransitionDraftToHandedOverThrowsLifecycleException() {
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRowVersion(0);

        stateEngine.transition(context, app, ApplicationStatus.HANDED_OVER, "ILLEGAL_JUMP", "Direct leap not allowed", null);
    }

    @Test(expected = AdmissionsLifecycleException.class)
    public void testTerminalHandedOverStateCannotBeReversed() {
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setStatus(ApplicationStatus.HANDED_OVER);
        app.setRowVersion(1);

        stateEngine.transition(context, app, ApplicationStatus.DRAFT, "REVERSE", "Cannot reverse handed over", null);
    }

    @Test
    public void testWithdrawalAllowedFromDraftOrSubmitted() {
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setRowVersion(0);

        stateEngine.transition(context, app, ApplicationStatus.WITHDRAWN, "WITHDRAW", "Applicant withdrew", null);
        assertEquals(ApplicationStatus.WITHDRAWN, app.getStatus());
    }

    @Test
    public void testIdempotentSameStatusNoOp() {
        ApplicationEntity app = new ApplicationEntity();
        app.setTenantId(tenantId);
        app.setId(applicationId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setRowVersion(0);

        stateEngine.transition(context, app, ApplicationStatus.SUBMITTED, "SUBMIT", "Duplicate call", null);
        assertEquals(ApplicationStatus.SUBMITTED, app.getStatus());
        assertEquals(0, (int) app.getRowVersion());
    }
}
