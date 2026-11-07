package com.campx.guardian.persistence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianDomainService.GuardianLink;

import java.util.List;
import java.util.UUID;

/**
 * Committed access to {@code core.persons} and {@code stu.guardianships}.
 * Implementations must keep the caller's tenant on every read and write.
 */
public interface GuardianPersistence {

    void saveGuardian(Actor actor, GuardianLink link);

    void updateGuardian(Actor actor, GuardianLink link);

    GuardianLink findGuardian(Actor actor, UUID guardianshipId);

    List<GuardianLink> findGuardians(Actor actor, UUID studentId);
}
