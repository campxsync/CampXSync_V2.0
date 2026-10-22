package com.campx.student.repository;

import com.campx.student.model.PersonEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link PersonRepository} for isolated unit and mock testing.
 * Strictly enforces tenant isolation boundaries.
 */
public class InMemoryPersonRepository implements PersonRepository {

    // Key: tenantId + ":" + personId
    private final Map<String, PersonEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID personId) {
        return tenantId.toString() + ":" + personId.toString();
    }

    @Override
    public PersonEntity save(UserSecurityContext context, PersonEntity person) {
        if (person == null) throw new IllegalArgumentException("Person cannot be null");
        UUID tenantId = context.getTenantId();
        person.setTenantId(tenantId);

        if (person.getId() == null) {
            person.setId(UUID.randomUUID());
            person.setCreatedAt(OffsetDateTime.now());
            person.setCreatedBy(context.getUserId());
        }
        person.setUpdatedAt(OffsetDateTime.now());
        person.setUpdatedBy(context.getUserId());

        store.put(key(tenantId, person.getId()), person);
        return person;
    }

    @Override
    public Optional<PersonEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        PersonEntity entity = store.get(key(context.getTenantId(), id));
        if (entity != null && entity.getDeletedAt() == null) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PersonEntity> findByEmail(UserSecurityContext context, String email) {
        if (email == null || email.trim().isEmpty()) return Optional.empty();
        UUID tenantId = context.getTenantId();
        String normalized = email.trim().toLowerCase();

        return store.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId())
                        && p.getDeletedAt() == null
                        && p.getPrimaryEmail() != null
                        && p.getPrimaryEmail().trim().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(UserSecurityContext context, String email) {
        return findByEmail(context, email).isPresent();
    }
}
