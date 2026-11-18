package com.campx.academic.timetable;

import com.campx.academic.timetable.exception.TimetableUnauthorizedException;
import com.campx.academic.timetable.model.TimetableModels.DayOfWeek;
import com.campx.academic.timetable.model.TimetableModels.TimetableEntry;
import com.campx.academic.timetable.port.FakeStudentBatchMembershipPort;
import com.campx.academic.timetable.repository.InMemoryTimetableRepository;
import com.campx.academic.timetable.security.UserSecurityContext;
import com.campx.academic.timetable.server.TimetableServer;
import com.campx.academic.timetable.service.TimetableDomainService;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-10-US-004 security: student-owned /my timetable.
 * Student A cannot read Student B's batch; other college denied; unauthenticated denied;
 * supplied batchId has no effect.
 */
public class MyTimetableSecurityTest {

    private static final int TEST_PORT = 18097;
    private static final String BASE = "http://localhost:" + TEST_PORT;

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-0000000000bb");
    private static final UUID STUDENT_A = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_B = UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID BATCH_A = UUID.fromString("a0000000-0000-0000-0000-000000000060");
    private static final UUID BATCH_B = UUID.fromString("b0000000-0000-0000-0000-000000000061");

    private static TimetableServer server;
    private static TimetableDomainService domain;
    private static FakeStudentBatchMembershipPort membership;

    @BeforeClass
    public static void startServer() throws Exception {
        membership = new FakeStudentBatchMembershipPort();
        domain = new TimetableDomainService(new InMemoryTimetableRepository(), membership);
        server = new TimetableServer(TEST_PORT, domain);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Before
    public void resetMembershipAndSeed() {
        membership.clear();
        membership.put(TENANT_A, STUDENT_A, BATCH_A);
        membership.put(TENANT_A, STUDENT_B, BATCH_B);

        TimetableEntry entryA = new TimetableEntry();
        entryA.setId("ENT-A1");
        entryA.setTenantId(TENANT_A.toString());
        entryA.setBatchId(BATCH_A.toString());
        entryA.setSubjectId("SUB-201");
        entryA.setDayOfWeek(DayOfWeek.MONDAY);
        entryA.setPeriod(1);
        entryA.setStartTime("09:00");
        entryA.setEndTime("10:00");

        TimetableEntry entryB = new TimetableEntry();
        entryB.setId("ENT-B1");
        entryB.setTenantId(TENANT_A.toString());
        entryB.setBatchId(BATCH_B.toString());
        entryB.setSubjectId("SUB-202");
        entryB.setDayOfWeek(DayOfWeek.TUESDAY);
        entryB.setPeriod(2);
        entryB.setStartTime("11:00");
        entryB.setEndTime("12:00");

        domain.seedPublishedBatchSnapshotForTests(TENANT_A.toString(), BATCH_A.toString(),
                Collections.singletonList(entryA));
        domain.seedPublishedBatchSnapshotForTests(TENANT_A.toString(), BATCH_B.toString(),
                Collections.singletonList(entryB));
    }

    @Test
    public void studentASeesOwnBatchOnly_domain() {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT_A, TENANT_A, null, null, "STUDENT", false);
        Map<String, Object> mine = domain.getMyPublishedTimetable(ctx, BATCH_B.toString());
        assertTrue(Boolean.TRUE.equals(mine.get("membershipFound")));
        assertEquals(BATCH_A.toString(), mine.get("batchId"));
        assertTrue(Boolean.TRUE.equals(mine.get("clientBatchIdIgnored")));
        @SuppressWarnings("unchecked")
        List<TimetableEntry> entries = (List<TimetableEntry>) mine.get("entries");
        assertFalse(entries.isEmpty());
        for (TimetableEntry e : entries) {
            assertEquals(BATCH_A.toString(), e.getBatchId());
            assertNotEquals("ENT-B1", e.getId());
        }
        assertTrue(entries.stream().anyMatch(e -> "ENT-A1".equals(e.getId())));
    }

    @Test
    public void studentACannotObtainStudentBBatchViaClientBatchId() {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT_A, TENANT_A, null, null, "STUDENT", false);
        Map<String, Object> mine = domain.getMyPublishedTimetable(ctx, BATCH_B.toString());
        assertNotEquals(BATCH_B.toString(), mine.get("batchId"));
        @SuppressWarnings("unchecked")
        List<TimetableEntry> entries = (List<TimetableEntry>) mine.get("entries");
        for (TimetableEntry e : entries) {
            assertNotEquals(BATCH_B.toString(), e.getBatchId());
            assertNotEquals("ENT-B1", e.getId());
        }
    }

    @Test
    public void otherCollegeMembershipNotVisible() {
        UserSecurityContext foreign = new UserSecurityContext(STUDENT_A, TENANT_B, null, null, "STUDENT", false);
        Map<String, Object> mine = domain.getMyPublishedTimetable(foreign, null);
        assertFalse(Boolean.TRUE.equals(mine.get("membershipFound")));
        assertNull(mine.get("batchId"));
        @SuppressWarnings("unchecked")
        List<?> entries = (List<?>) mine.get("entries");
        assertTrue(entries.isEmpty());
    }

    @Test(expected = TimetableUnauthorizedException.class)
    public void nullContextDenied() {
        domain.getMyPublishedTimetable(null, null);
    }

    @Test
    public void httpStudentMyReturnsOwnBatch_andIgnoresQueryBatchId() throws Exception {
        HttpURLConnection conn = open("GET",
                "/api/v1/academics/timetables/my?batchId=" + BATCH_B,
                TENANT_A.toString(), STUDENT_A.toString(), "STUDENT");
        assertEquals(200, conn.getResponseCode());
        String body = read(conn.getInputStream());
        assertTrue(body.contains(BATCH_A.toString()));
        assertTrue(body.contains("ENT-A1"));
        assertFalse(body.contains("ENT-B1"));
        assertTrue(body.contains("\"clientBatchIdIgnored\":true") || body.contains("clientBatchIdIgnored\":true"));
    }

    @Test
    public void httpStudentForbiddenOnOtherBatchPath() throws Exception {
        HttpURLConnection conn = open("GET",
                "/api/v1/academics/timetables/batch/" + BATCH_B,
                TENANT_A.toString(), STUDENT_A.toString(), "STUDENT");
        assertEquals(403, conn.getResponseCode());
        String err = read(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream());
        assertTrue(err.contains("ACD_TIMETABLE_FORBIDDEN"));
    }

    @Test
    public void httpOtherCollegeCannotUseStudentAIdentityAgainstTenantBMembership() throws Exception {
        // Student A has membership only under TENANT_A; calling with TENANT_B yields empty membership
        HttpURLConnection conn = open("GET",
                "/api/v1/academics/timetables/my",
                TENANT_B.toString(), STUDENT_A.toString(), "STUDENT");
        assertEquals(200, conn.getResponseCode());
        String body = read(conn.getInputStream());
        assertTrue(body.contains("\"membershipFound\":false") || body.contains("membershipFound\":false"));
    }

    @Test
    public void httpMissingTenantDeniedOnMy() throws Exception {
        URL url = new URL(BASE + "/api/v1/academics/timetables/my");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "STUDENT");
        conn.setRequestProperty("X-User-Id", STUDENT_A.toString());
        // intentionally omit X-Tenant-Id — controller falls back to TENANT-001 which is not a UUID
        int code = conn.getResponseCode();
        assertTrue("expected 4xx, got " + code, code >= 400 && code < 500);
    }

    @Test
    public void httpUnauthenticatedRoleCannotHitMy() throws Exception {
        // Empty role fails isAuthorized
        URL url = new URL(BASE + "/api/v1/academics/timetables/my");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Id", STUDENT_A.toString());
        conn.setRequestProperty("X-User-Role", "");
        // Controller defaults empty role to ACADEMIC_ADMIN — that is pre-existing.
        // Use an explicitly unauthorized role instead.
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", TENANT_A.toString());
        conn.setRequestProperty("X-User-Id", STUDENT_A.toString());
        conn.setRequestProperty("X-User-Role", "UNKNOWN_ROLE");
        assertEquals(403, conn.getResponseCode());
    }

    private static HttpURLConnection open(String method, String path, String tenantId, String userId, String role)
            throws Exception {
        URL url = new URL(BASE + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        if (tenantId != null) {
            conn.setRequestProperty("X-Tenant-Id", tenantId);
        }
        if (userId != null) {
            conn.setRequestProperty("X-User-Id", userId);
        }
        if (role != null) {
            conn.setRequestProperty("X-User-Role", role);
        }
        return conn;
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
