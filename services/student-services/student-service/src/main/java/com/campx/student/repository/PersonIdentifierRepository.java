package com.campx.student.repository;

import com.campx.student.model.PersonIdentifierEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for person identifiers in {@code core.person_identifiers} (STM01-US-008, STM01-US-009).
 */
public interface PersonIdentifierRepository {

    PersonIdentifierEntity save(UserSecurityContext context, PersonIdentifierEntity identifier);

    Optional<PersonIdentifierEntity> findById(UserSecurityContext context, UUID id);

    List<PersonIdentifierEntity> findByPersonId(UserSecurityContext context, UUID personId);

    Optional<PersonIdentifierEntity> findByPersonIdAndType(UserSecurityContext context, UUID personId, String idType);

    boolean existsByTypeAndValue(UserSecurityContext context, String idType, String rawValue);
}
