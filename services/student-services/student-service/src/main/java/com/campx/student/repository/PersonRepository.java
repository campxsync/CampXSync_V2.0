package com.campx.student.repository;

import com.campx.student.model.PersonEntity;
import com.campx.student.security.UserSecurityContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for canonical human identity in {@code core.persons}.
 */
public interface PersonRepository {

    PersonEntity save(UserSecurityContext context, PersonEntity person);

    Optional<PersonEntity> findById(UserSecurityContext context, UUID id);

    Optional<PersonEntity> findByEmail(UserSecurityContext context, String email);

    boolean existsByEmail(UserSecurityContext context, String email);
}
