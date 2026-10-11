package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.NumberSequence;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing sequence number generators (Item 24: {@code core.number_sequences}).
 */
public interface NumberSequenceRepository {

    NumberSequence createSequence(UserSecurityContext context, NumberSequence sequence);

    Optional<NumberSequence> findById(UserSecurityContext context, String id);

    Optional<NumberSequence> findByScope(UserSecurityContext context, String scopeKey, String collegeId);

    List<NumberSequence> listSequences(UserSecurityContext context);

    NumberSequence updateSequence(UserSecurityContext context, NumberSequence sequence);

    void deleteSequence(UserSecurityContext context, String id);

    /**
     * Atomically increments next_value and returns the next formatted sequence number string
     * using the sequence's prefix, padding, and suffix.
     *
     * @param context   caller security context
     * @param scopeKey  scope key identifier
     * @param collegeId optional college UUID
     * @return generated formatted sequence string
     */
    String generateNextNumber(UserSecurityContext context, String scopeKey, String collegeId);
}
