package com.campx.student;

import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for Document Metadata Attachment, MIME Validation, Size Constraints, and Photo Reference.
 * Covers: STM01-US-020, STM01-US-021, STM01-US-022, STM01-US-042
 */
public class StudentDocumentAndPhotoTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Karan");
        req.setLastName("Malhotra");
        req.setPrimaryEmail("karan.malhotra@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
    }

    @Test
    public void testAttachValidDocumentMetadata() {
        AttachDocumentRequest req = new AttachDocumentRequest();
        req.setTitle("transcript_2025.pdf");
        req.setDocumentType("HIGH_SCHOOL_TRANSCRIPT");
        req.setStorageBucket("student-docs");
        req.setStoragePath("transcripts/transcript_2025.pdf");
        req.setMimeType("application/pdf");
        req.setSizeBytes(1024 * 500); // 500 KB

        StudentDocumentEntity doc = service.attachDocument(staffContext, student.getId(), req);
        assertNotNull(doc.getId());
        assertEquals("HIGH_SCHOOL_TRANSCRIPT", doc.getDocumentType());
        assertEquals("transcript_2025.pdf", doc.getTitle());

        List<StudentDocumentEntity> docs = service.getDocuments(staffContext, student.getId());
        assertEquals(1, docs.size());
    }

    @Test(expected = StudentValidationException.class)
    public void testDisallowedMimeTypeRejected() {
        AttachDocumentRequest req = new AttachDocumentRequest();
        req.setTitle("resume.exe");
        req.setDocumentType("RESUME");
        req.setStorageBucket("student-docs");
        req.setStoragePath("resumes/resume.exe");
        req.setMimeType("application/x-msdownload"); // disallowed executable MIME!
        req.setSizeBytes(2048);

        service.attachDocument(staffContext, student.getId(), req);
    }

    @Test(expected = StudentValidationException.class)
    public void testExcessiveFileSizeRejected() {
        AttachDocumentRequest req = new AttachDocumentRequest();
        req.setTitle("huge_scan.pdf");
        req.setDocumentType("CERTIFICATE");
        req.setStorageBucket("student-docs");
        req.setStoragePath("certificates/huge_scan.pdf");
        req.setMimeType("application/pdf");
        req.setSizeBytes(30 * 1024 * 1024); // 30 MB > 25 MB limit!

        service.attachDocument(staffContext, student.getId(), req);
    }

    @Test
    public void testUpdatePhotoDocumentReference() {
        UUID photoDocId = UUID.randomUUID();
        UpdateStudentProfileRequest req = new UpdateStudentProfileRequest();
        req.setPhotoDocumentId(photoDocId);

        StudentProfileResponse updated = service.updateStudentProfile(staffContext, student.getId(), req, null);
        assertEquals(photoDocId, updated.getPhotoDocumentId());
    }
}
