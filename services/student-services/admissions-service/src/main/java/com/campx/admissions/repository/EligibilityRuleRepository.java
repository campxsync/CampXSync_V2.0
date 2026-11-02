package com.campx.admissions.repository;

import com.campx.admissions.model.EligibilityRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EligibilityRuleRepository {
    EligibilityRuleEntity save(EligibilityRuleEntity rule);

    Optional<EligibilityRuleEntity> findById(UUID tenantId, UUID id);

    Optional<EligibilityRuleEntity> findLatestActiveByIntake(UUID tenantId, UUID intakeId);

    List<EligibilityRuleEntity> findAllByIntake(UUID tenantId, UUID intakeId);
}
