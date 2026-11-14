package com.campx.guardian;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GuardianDomainServiceTest {

    private GuardianDomainService service;
    private UUID tenant;
    private UUID student;
    private Actor registrar;
    private Actor studentActor;

    @Before
    public void setUp() {
        service = new GuardianDomainService();
        tenant = UUID.randomUUID();
        student = UUID.randomUUID();
        registrar = new Actor(tenant, UUID.randomUUID(), "COLLEGE_ADMIN", "corr-reg");
        studentActor = new Actor(tenant, student, "STUDENT", "corr-stu");
    }

    @Test
    public void registrarAddsGuardianAndStudentCanListOwn() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                "asha@example.com", "9000000000", true);
        assertEquals("MOTHER", link.relationship);
        assertEquals("ACTIVE", link.status);
        assertTrue(link.primary);
        assertEquals(1, service.listGuardians(studentActor, student).size());
        assertEquals("Asha", service.listGuardians(registrar, student).get(0).firstName);
    }

    @Test
    public void studentCannotAddGuardian() {
        try {
            service.addGuardian(studentActor, student, "Asha", "Rao", "mother", null, null, false);
            fail("Students must not add guardians");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void missingNameIsRejected() {
        try {
            service.addGuardian(registrar, student, " ", "Rao", "father", null, null, false);
            fail("A blank name must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("FIELD_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void registrarUpdatesRelationshipAndPrimaryFlag() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                "asha@example.com", "9000000000", true);
        GuardianDomainService.GuardianLink updated = service.updateGuardian(registrar, link.id, "aunt", false);
        assertEquals("AUNT", updated.relationship);
        assertTrue(!updated.primary);
        assertEquals("Asha", updated.firstName);
        assertEquals("AUNT", service.listGuardians(registrar, student).get(0).relationship);
    }

    @Test
    public void registrarSubmitsAllowedEvidence() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        GuardianDomainService.EvidenceRecord record = service.submitEvidence(registrar, link.id, "birth certificate");
        assertEquals("BIRTH_CERTIFICATE", record.documentType);
        assertEquals("SUBMITTED", record.status);
        assertEquals(link.id, record.guardianshipId);
    }

    @Test
    public void registrarVerifiesSubmittedEvidence() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "PASSPORT");
        GuardianDomainService.ReviewDecision decision = service.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        assertEquals("VERIFIED", decision.decision);
        assertEquals("APPROVED", decision.storedStatus);
        assertEquals("IDENTITY_CONFIRMED", decision.reasonCode);
    }

    @Test
    public void reviewerCannotDecideTheirOwnRelationship() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "PASSPORT");
        Actor party = new Actor(tenant, link.guardianPersonId, "COLLEGE_ADMIN", "corr-party");
        try {
            service.reviewEvidence(party, link.id, "VERIFY", "IDENTITY_CONFIRMED");
            fail("A party to the relationship must not review it");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REVIEWER_IS_PARTY", ex.getCode());
        }
    }

    @Test
    public void unsupportedEvidenceTypeIsRejected() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        try {
            service.submitEvidence(registrar, link.id, "utility bill");
            fail("An unsupported document type must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("DOCUMENT_TYPE_UNSUPPORTED", ex.getCode());
        }
    }

    @Test
    public void studentCannotUpdateAnotherStudentsGuardian() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, true);
        try {
            service.updateGuardian(studentActor, link.id, "uncle", true);
            fail("A student must not update another student's guardian");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_UPDATE_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentEndsOwnRelationshipWithoutDeletingIt() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        GuardianDomainService.Deactivation ended = service.deactivateGuardian(studentActor, link.id, "STUDENT_REQUEST");
        assertEquals("ENDED", ended.link.status);
        assertTrue(!ended.link.primary);
        assertEquals("STUDENT_REQUEST", ended.reasonCode);
        assertTrue(ended.accessRevoked);
        assertTrue(!ended.hardDeleted);
        assertTrue(ended.noticeRecorded);
        assertEquals("ENDED", service.listGuardians(studentActor, student).get(0).status);
        try {
            service.deactivateGuardian(studentActor, link.id, "STUDENT_REQUEST");
            fail("An ended relationship must not be ended again");
        } catch (GuardianException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("GUARDIAN_ALREADY_ENDED", ex.getCode());
        }
    }

    @Test
    public void registrarEndsRelationshipForADispute() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        GuardianDomainService.Deactivation ended = service.deactivateGuardian(registrar, link.id, "DISPUTE");
        assertEquals("ENDED", ended.link.status);
        assertEquals("DISPUTE", ended.reasonCode);
        assertEquals(1, service.listGuardians(registrar, student).size());
    }

    @Test
    public void institutionCannotUseTheStudentReason() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        try {
            service.deactivateGuardian(registrar, link.id, "STUDENT_REQUEST");
            fail("A registrar must use the dispute reason");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("REASON_NOT_ALLOWED", ex.getCode());
        }
        assertEquals("ACTIVE", service.listGuardians(registrar, student).get(0).status);
    }

    @Test
    public void studentCannotEndAnotherStudentsGuardian() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, true);
        try {
            service.deactivateGuardian(studentActor, link.id, "STUDENT_REQUEST");
            fail("A student must not end another student's guardian");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_DEACTIVATE_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarMarksDisputedWithoutEditingTheEarlierEvidence() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        GuardianDomainService.EvidenceRecord earlier = service.submitEvidence(registrar, link.id, "PASSPORT");
        GuardianDomainService.StateChange change = service.markRelationshipState(registrar, link.id, "disputed");
        assertEquals("DISPUTED", change.requestedState);
        assertEquals("SUSPENDED", change.storedStatus);
        assertEquals("SUSPENDED", change.link.status);
        assertTrue(change.accessRevoked);
        assertTrue(!change.previousDocumentEdited);
        assertTrue(!earlier.documentId.equals(change.documentId));
        assertEquals("SUSPENDED", service.listGuardians(registrar, student).get(0).status);
    }

    @Test
    public void expiredRelationshipIsStoredAsEnded() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        GuardianDomainService.StateChange change = service.markRelationshipState(registrar, link.id, "EXPIRED");
        assertEquals("EXPIRED", change.requestedState);
        assertEquals("ENDED", change.storedStatus);
    }

    @Test
    public void endedRelationshipCannotBeMarkedAgain() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.deactivateGuardian(registrar, link.id, "DISPUTE");
        try {
            service.markRelationshipState(registrar, link.id, "DISPUTED");
            fail("An ended relationship must not be marked again");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("RELATIONSHIP_NOT_ACTIVE", ex.getCode());
        }
    }

    @Test
    public void studentCannotMarkARelationshipState() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        try {
            service.markRelationshipState(studentActor, link.id, "SUSPENDED");
            fail("A student must not mark the relationship state");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentMaintainsAnEmergencyContactWithoutGrantingAccess() {
        GuardianDomainService.EmergencyContact added = service.addEmergencyContact(studentActor, student, "Ravi", "Uncle", "98765 43210");
        assertEquals("ACTIVE", added.status);
        assertEquals("9876543210", added.phone);
        GuardianDomainService.EmergencyContact changed = service.updateEmergencyContact(studentActor, added.id, student, "Ravi", "Uncle", "9876543211");
        assertEquals("9876543211", changed.phone);
        GuardianDomainService.EmergencyContact removed = service.removeEmergencyContact(studentActor, added.id, student);
        assertEquals("INACTIVE", removed.status);
        assertEquals(1, service.listEmergencyContacts(studentActor, student).size());
        assertEquals("INACTIVE", service.listEmergencyContacts(studentActor, student).get(0).status);
    }

    @Test
    public void shortPhoneIsRejected() {
        try {
            service.addEmergencyContact(studentActor, student, "Ravi", "Uncle", "12345");
            fail("A short phone must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("PHONE_INVALID", ex.getCode());
        }
    }

    @Test
    public void studentCannotMaintainAnotherStudentsEmergencyContact() {
        UUID other = UUID.randomUUID();
        try {
            service.addEmergencyContact(studentActor, other, "Ravi", "Uncle", "9876543210");
            fail("A student must not add a contact for another student");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("EMERGENCY_CONTACT_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarGrantsSelectedAccessAfterVerification() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        service.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        java.util.List<String> categories = java.util.Arrays.asList("ACADEMICS");
        GuardianDomainService.AccessGrant grant = service.grantAccess(registrar, link.id, categories, "FAMILY_SUPPORT", "2027-06-30");
        assertTrue(grant.viewAcademics);
        assertTrue(!grant.viewFees);
        assertTrue(!grant.payFees);
        assertTrue(!grant.everythingGranted);
        assertEquals("FAMILY_SUPPORT", grant.purpose);
        assertEquals("2027-06-30", grant.validTo);
    }

    @Test
    public void unverifiedRelationshipCannotReceiveAccess() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        try {
            service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
            fail("Unverified evidence must not receive access");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("RELATIONSHIP_NOT_VERIFIED", ex.getCode());
        }
    }

    @Test
    public void blanketAccessIsRejected() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        service.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        try {
            service.grantAccess(registrar, link.id, java.util.Arrays.asList("EVERYTHING"), "FAMILY_SUPPORT", "2027-06-30");
            fail("A blanket grant must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("CATEGORY_UNSUPPORTED", ex.getCode());
        }
    }

    @Test
    public void elapsedAccessPeriodExpiresOnReadAndStaysDenied() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
        GuardianDomainService timed = new GuardianDomainService(clock, null, null);
        GuardianDomainService.GuardianLink link = timed.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        timed.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        timed.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        timed.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
        timed.setAccessPeriod(registrar, link.id, "2026-11-01", "2026-11-07");
        GuardianDomainService.AccessDecision expired = timed.currentAccess(registrar, link.id);
        assertEquals("ACTIVE", expired.relationshipStatus);
        assertEquals("EXPIRED", expired.accessState);
        assertTrue(!expired.viewAcademics);
        assertTrue(!expired.accessAllowed);
        GuardianDomainService.AccessDecision again = timed.currentAccess(registrar, link.id);
        assertEquals("EXPIRED", again.accessState);
        assertTrue(!again.accessAllowed);
    }

    @Test
    public void studentCannotSetAnotherStudentsAccessPeriod() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        try {
            service.setAccessPeriod(studentActor, link.id, "2026-11-01", "2027-06-30");
            fail("A student must not set another student's access period");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ACCESS_PERIOD_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentRevokesOwnGuardianAccessAndASecondRevokeIsRejected() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        service.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS", "FEES"), "FAMILY_SUPPORT", "2027-06-30");
        GuardianDomainService.AccessDecision revoked = service.revokeAccess(studentActor, link.id);
        assertEquals("ACTIVE", revoked.relationshipStatus);
        assertEquals("REVOKED", revoked.accessState);
        assertTrue(!revoked.viewAcademics);
        assertTrue(!revoked.viewFees);
        assertTrue(!revoked.accessAllowed);
        try {
            service.revokeAccess(studentActor, link.id);
            fail("A second revoke must be rejected");
        } catch (GuardianException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("ACCESS_ALREADY_REVOKED", ex.getCode());
        }
    }

    @Test
    public void studentCannotRevokeAnotherStudentsGuardianAccess() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        try {
            service.revokeAccess(studentActor, link.id);
            fail("A student must not revoke another student's guardian access");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ACCESS_PERIOD_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentCannotGrantGuardianAccess() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        try {
            service.grantAccess(studentActor, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
            fail("A student must not grant guardian access from the registrar action");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void authorizedViewReturnsOnlyGrantedCategories() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
        service.setAccessPeriod(registrar, link.id, "2026-11-01", "2027-06-30");
        GuardianDomainService.AuthorizedView view = service.viewAuthorizedInformation(studentActor, link.id);
        assertEquals(student, view.studentId);
        assertEquals(1, view.categories.size());
        assertEquals("ACADEMICS", view.categories.get(0));
        assertTrue(!view.categories.contains("FEES"));
        assertTrue(!view.categories.contains("FEE_PAYMENT"));
        assertTrue(!view.reviewerNotesIncluded);
        assertTrue(!view.sourceRecordsIncluded);
    }

    @Test
    public void revokedGrantCannotBeViewed() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS", "FEES"), "FAMILY_SUPPORT", "2027-06-30");
        service.revokeAccess(studentActor, link.id);
        try {
            service.viewAuthorizedInformation(studentActor, link.id);
            fail("A revoked grant must not return student information");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_VIEW_DENIED", ex.getCode());
        }
    }

    @Test
    public void elapsedGrantCannotBeViewed() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
        GuardianDomainService timed = new GuardianDomainService(clock, null, null);
        GuardianDomainService.GuardianLink link = timed.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        timed.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        timed.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        timed.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
        timed.setAccessPeriod(registrar, link.id, "2026-11-01", "2026-11-07");
        try {
            timed.viewAuthorizedInformation(registrar, link.id);
            fail("An elapsed grant must not return student information");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_VIEW_DENIED", ex.getCode());
        }
    }

    @Test
    public void studentCannotViewAnotherStudentsAuthorizedInformation() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        try {
            service.viewAuthorizedInformation(studentActor, link.id);
            fail("A student must not view another student's authorized information");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ACCESS_PERIOD_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void facultyCannotViewAuthorizedInformation() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-06-30");
        Actor faculty = new Actor(tenant, UUID.randomUUID(), "FACULTY", "corr-fac");
        try {
            service.viewAuthorizedInformation(faculty, link.id);
            fail("Faculty must not view the guardian information grant");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ACCESS_PERIOD_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void auditorReviewsClaimVerificationGrantAndRevocation() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS", "FEES"), "FAMILY_SUPPORT", "2027-06-30");
        service.revokeAccess(studentActor, link.id);
        java.util.List<GuardianDomainService.HistoryEntry> rows = service.listRelationshipHistory(registrar, student);
        assertEquals(4, rows.size());
        assertEquals("CLAIM", rows.get(0).kind);
        assertEquals("VERIFICATION", rows.get(1).kind);
        assertEquals("SUBMITTED", rows.get(1).oldValue);
        assertEquals("APPROVED", rows.get(1).newValue);
        assertEquals("GRANT", rows.get(2).kind);
        assertEquals("NONE", rows.get(2).oldValue);
        assertEquals("ACADEMICS,FEES", rows.get(2).newValue);
        assertEquals("REVOCATION", rows.get(3).kind);
        assertEquals("ACADEMICS,FEES", rows.get(3).oldValue);
        assertEquals("NONE", rows.get(3).newValue);
        assertTrue(rows.get(3).readOnly);
    }

    @Test
    public void studentCannotReviewRelationshipHistory() {
        try {
            service.listRelationshipHistory(studentActor, student);
            fail("A student must not review relationship history");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("HISTORY_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void historyStaysWithTheRequestedStudent() {
        verifiedGuardian();
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink otherLink = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        service.submitEvidence(registrar, otherLink.id, "PASSPORT");
        service.reviewEvidence(registrar, otherLink.id, "VERIFY", "IDENTITY_CONFIRMED");
        java.util.List<GuardianDomainService.HistoryEntry> rows = service.listRelationshipHistory(registrar, student);
        assertEquals(2, rows.size());
        assertEquals(student, rows.get(0).studentId);
        assertEquals(student, rows.get(1).studentId);
    }

    private GuardianDomainService.GuardianLink verifiedGuardian() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother", null, null, true);
        service.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        service.reviewEvidence(registrar, link.id, "VERIFY", "IDENTITY_CONFIRMED");
        return link;
    }

    @Test
    public void studentCannotListAnotherStudentsGuardians() {
        UUID other = UUID.randomUUID();
        service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        try {
            service.listGuardians(studentActor, other);
            fail("A student must not list another student's guardians");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_VIEW_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarCanLinkOneGuardianToSeveralStudents() {
        // Step 1: Add guardian for student 1
        GuardianDomainService.GuardianLink link1 = service.addGuardian(registrar, student, "Asha", "Rao", "MOTHER",
                "asha@example.com", "9000000000", true);
        UUID guardianPersonId = link1.guardianPersonId;

        // Step 2: Link same guardian person to student 2 (sibling)
        UUID student2 = UUID.randomUUID();
        GuardianDomainService.GuardianLink link2 = service.linkExistingGuardian(registrar, student2, guardianPersonId, "MOTHER", false);

        assertEquals(guardianPersonId, link2.guardianPersonId);
        assertEquals(student2, link2.studentId);
        assertEquals("MOTHER", link2.relationship);
        assertEquals("ACTIVE", link2.status);
        assertEquals("Asha", link2.firstName);

        // Step 3: Verify listStudentsForGuardian returns both student links
        java.util.List<GuardianDomainService.GuardianLink> linkedStudents = service.listStudentsForGuardian(registrar, guardianPersonId);
        assertEquals(2, linkedStudents.size());

        // Step 4: Verify student 2's own guardian list contains the link
        java.util.List<GuardianDomainService.GuardianLink> student2Guardians = service.listGuardians(registrar, student2);
        assertEquals(1, student2Guardians.size());
        assertEquals(guardianPersonId, student2Guardians.get(0).guardianPersonId);
    }

    @Test
    public void studentCannotLinkExistingGuardian() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "MOTHER", null, null, true);
        UUID student2 = UUID.randomUUID();
        try {
            service.linkExistingGuardian(studentActor, student2, link.guardianPersonId, "MOTHER", false);
            fail("Students must not link existing guardians");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void duplicateActiveLinkToSameStudentIsRejected() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "MOTHER", null, null, true);
        try {
            service.linkExistingGuardian(registrar, student, link.guardianPersonId, "MOTHER", false);
            fail("Duplicate active link must be rejected");
        } catch (GuardianException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("DUPLICATE_GUARDIAN_LINK", ex.getCode());
        }
    }

    @Test
    public void missingFieldsInLinkExistingAreRejected() {
        UUID personId = UUID.randomUUID();
        try {
            service.linkExistingGuardian(registrar, null, personId, "MOTHER", false);
            fail("Missing studentId must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("STUDENT_REQUIRED", ex.getCode());
        }

        try {
            service.linkExistingGuardian(registrar, student, null, "MOTHER", false);
            fail("Missing guardianPersonId must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("GUARDIAN_PERSON_REQUIRED", ex.getCode());
        }

        try {
            service.linkExistingGuardian(registrar, student, personId, " ", false);
            fail("Blank relationship must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("FIELD_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void unauthorizedCallerCannotListStudentsForGuardian() {
        UUID personId = UUID.randomUUID();
        try {
            service.listStudentsForGuardian(studentActor, personId);
            fail("Unrelated student must not list students for arbitrary guardian");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_VIEW_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-05 story-scoped coverage (US-001…014 partials) ---

    @Test
    public void stm05Us001_registrarAddsGuardian() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Neha", "Shah", "mother",
                "neha@example.com", "9000000001", true);
        assertEquals("ACTIVE", link.status);
        assertEquals(1, service.listGuardians(studentActor, student).size());
    }

    @Test
    public void stm05Us002_updateGuardianRelationship() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                null, null, false);
        GuardianDomainService.GuardianLink updated = service.updateGuardian(registrar, link.id, "father", true);
        assertEquals("FATHER", updated.relationship);
        assertTrue(updated.primary);
    }

    @Test
    public void stm05Us003_004_submitAndReviewEvidence() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                null, null, true);
        service.submitEvidence(registrar, link.id, "BIRTH_CERTIFICATE");
        GuardianDomainService.ReviewDecision review = service.reviewEvidence(registrar, link.id, "VERIFY",
                "IDENTITY_CONFIRMED");
        assertEquals("VERIFIED", review.decision);
    }

    @Test
    public void stm05Us005_deactivateGuardianKeepsHistory() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        GuardianDomainService.Deactivation ended = service.deactivateGuardian(registrar, link.id, "DISPUTE");
        assertEquals("ENDED", ended.link.status);
        assertTrue(!ended.hardDeleted);
    }

    @Test
    public void stm05Us006_008_maintainAndRetrieveEmergencyContacts() {
        GuardianDomainService.EmergencyContact contact = service.addEmergencyContact(studentActor, student,
                "Priya Rao", "mother", "9000000011");
        assertEquals("ACTIVE", contact.status);
        assertEquals(1, service.listEmergencyContacts(studentActor, student).size());
        service.updateEmergencyContact(studentActor, contact.id, student, "Priya Rao", "mother", "9000000012");
        GuardianDomainService.EmergencyContact removed = service.removeEmergencyContact(studentActor, contact.id, student);
        assertEquals("INACTIVE", removed.status);
        assertEquals("INACTIVE", service.listEmergencyContacts(studentActor, student).get(0).status);
    }

    @Test
    public void stm05Us009_010_grantAccessAndStudentSetsConsentPeriod() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        GuardianDomainService.AccessGrant grant = service.grantAccess(registrar, link.id,
                java.util.Arrays.asList("ACADEMICS", "FEES"), "FAMILY_SUPPORT", "2027-12-31");
        assertTrue(grant.viewAcademics);
        assertTrue(grant.viewFees);
        GuardianDomainService.AccessDecision period = service.setAccessPeriod(studentActor, link.id,
                "2026-01-01", "2027-12-31");
        assertEquals("PERIOD_SET", period.accessState);
    }

    @Test
    public void stm05Us011_012_revokeAccessAndAuthorizedView() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "ACADEMIC_PROGRESS", "2027-12-31");
        service.setAccessPeriod(registrar, link.id, "2026-11-01", "2027-12-31");
        GuardianDomainService.AuthorizedView view = service.viewAuthorizedInformation(studentActor, link.id);
        assertTrue(view.categories.contains("ACADEMICS"));
        service.revokeAccess(studentActor, link.id);
        try {
            service.viewAuthorizedInformation(studentActor, link.id);
            fail("revoked grant must deny view");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void stm05Us013_014_historyAndRefuseBlanketGrant() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        try {
            service.grantAccess(registrar, link.id, java.util.Arrays.asList("EVERYTHING"), "FAMILY_SUPPORT", "2027-12-31");
            fail("blanket grant must be refused");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("CATEGORY_UNSUPPORTED", ex.getCode());
        }
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-12-31");
        assertTrue(service.listRelationshipHistory(registrar, student).size() >= 1);
    }

    // --- STM-05-EN-003 Consent lifecycle, verification and revocation enforcement ---

    @Test
    public void stm05En003_grantPeriodThenRevokeDeniesAuthorizedView() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS", "FEES"), "FAMILY_SUPPORT", "2027-12-31");
        GuardianDomainService.AccessDecision period = service.setAccessPeriod(studentActor, link.id,
                "2026-11-01", "2027-12-31");
        assertEquals("PERIOD_SET", period.accessState);
        assertTrue(service.viewAuthorizedInformation(studentActor, link.id).categories.contains("ACADEMICS"));

        GuardianDomainService.AccessDecision revoked = service.revokeAccess(studentActor, link.id);
        assertEquals("REVOKED", revoked.accessState);
        assertTrue(!revoked.accessAllowed);
        try {
            service.viewAuthorizedInformation(studentActor, link.id);
            fail("Revoked consent must deny authorized view");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void stm05En003_elapsedAccessPeriodExpiresAndStaysDenied() {
        elapsedAccessPeriodExpiresOnReadAndStaysDenied();
    }

    @Test
    public void stm05En003_secondRevokeRejected() {
        GuardianDomainService.GuardianLink link = verifiedGuardian();
        service.grantAccess(registrar, link.id, java.util.Arrays.asList("ACADEMICS"), "FAMILY_SUPPORT", "2027-12-31");
        service.revokeAccess(studentActor, link.id);
        try {
            service.revokeAccess(studentActor, link.id);
            fail("Second revoke must be rejected");
        } catch (GuardianException ex) {
            assertEquals(409, ex.getStatus());
        }
    }
}
