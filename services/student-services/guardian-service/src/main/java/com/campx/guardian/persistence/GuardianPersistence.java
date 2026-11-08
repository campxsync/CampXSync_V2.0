package com.campx.guardian.persistence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianDomainService.EvidenceRecord;
import com.campx.guardian.GuardianDomainService.EmergencyContact;
import com.campx.guardian.GuardianDomainService.AccessWindow;
import com.campx.guardian.GuardianDomainService.GuardianLink;
import com.campx.guardian.GuardianDomainService.HistoryEntry;

import java.util.List;
import java.util.UUID;

/**
 * Committed access to {@code core.persons} and {@code stu.guardianships}.
 * Implementations must keep the caller's tenant on every read and write.
 */
public interface GuardianPersistence {

    void saveGuardian(Actor actor, GuardianLink link);

    void updateGuardian(Actor actor, GuardianLink link);

    void endGuardianship(Actor actor, GuardianLink link);

    java.util.UUID openReverification(Actor actor, GuardianLink link, String requestedState, String storedStatus);

    EmergencyContact saveEmergencyContact(Actor actor, EmergencyContact contact);

    EmergencyContact updateEmergencyContact(Actor actor, EmergencyContact contact);

    EmergencyContact removeEmergencyContact(Actor actor, EmergencyContact contact);

    EmergencyContact findEmergencyContact(Actor actor, java.util.UUID studentId, java.util.UUID contactId);

    java.util.List<EmergencyContact> findEmergencyContacts(Actor actor, java.util.UUID studentId);

    GuardianLink findGuardian(Actor actor, UUID guardianshipId);

    EvidenceRecord saveEvidence(Actor actor, GuardianLink link, String documentType);

    java.util.UUID reviewEvidence(Actor actor, GuardianLink link, String storedStatus);

    boolean evidenceApproved(Actor actor, GuardianLink link);

    void grantAccess(Actor actor, GuardianLink link, boolean viewAcademics, boolean viewFees, boolean payFees, java.time.LocalDate validTo);

    void saveAccessPeriod(Actor actor, GuardianLink link, java.time.LocalDate validFrom, java.time.LocalDate validTo);

    AccessWindow findAccess(Actor actor, GuardianLink link);

    void expireElapsedAccess(Actor actor, GuardianLink link, java.time.LocalDate today);

    void revokeAccess(Actor actor, GuardianLink link);

    void appendHistory(Actor actor, HistoryEntry entry);

    List<HistoryEntry> findHistory(Actor actor, UUID studentId);

    List<GuardianLink> findGuardians(Actor actor, UUID studentId);
}
