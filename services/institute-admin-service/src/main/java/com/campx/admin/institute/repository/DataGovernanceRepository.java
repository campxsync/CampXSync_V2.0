package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.DataClassification;
import com.campx.admin.institute.model.InstituteModels.DataRetentionPolicy;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing platform data governance configurations (Item 28:
 * {@code cfg.data_retention_policies} and {@code cfg.data_classifications}).
 */
public interface DataGovernanceRepository {

    DataRetentionPolicy createRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy);

    Optional<DataRetentionPolicy> findRetentionPolicyById(UserSecurityContext context, String id);

    Optional<DataRetentionPolicy> findRetentionPolicyByCode(UserSecurityContext context, String policyCode);

    List<DataRetentionPolicy> listRetentionPolicies(UserSecurityContext context);

    DataRetentionPolicy updateRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy);

    void deleteRetentionPolicy(UserSecurityContext context, String id);

    DataClassification createClassification(UserSecurityContext context, DataClassification classification);

    Optional<DataClassification> findClassificationById(UserSecurityContext context, String id);

    Optional<DataClassification> findClassificationByCode(UserSecurityContext context, String classificationCode);

    List<DataClassification> listClassifications(UserSecurityContext context);

    DataClassification updateClassification(UserSecurityContext context, DataClassification classification);

    void deleteClassification(UserSecurityContext context, String id);
}
