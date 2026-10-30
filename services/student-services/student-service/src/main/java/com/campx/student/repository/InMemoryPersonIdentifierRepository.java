package com.campx.student.repository;

import com.campx.student.model.PersonIdentifierEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory repository for person identifiers (STM01-US-008, STM01-US-009).
 */
public class InMemoryPersonIdentifierRepository implements PersonIdentifierRepository {

    // Key: tenantId + ":" + id
    private final Map<String, PersonIdentifierEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public PersonIdentifierEntity save(UserSecurityContext context, PersonIdentifierEntity identifier) {
        if (identifier == null) throw new IllegalArgumentException("Identifier cannot be null");
        UUID tenantId = context.getTenantId();
        identifier.setTenantId(tenantId);

        if (identifier.getId() == null) {
            identifier.setId(UUID.randomUUID());
            identifier.setCreatedAt(OffsetDateTime.now());
        }
        identifier.setUpdatedAt(OffsetDateTime.now());
        identifier.setRowVersion(identifier.getRowVersion() + 1);

        store.put(key(tenantId, identifier.getId()), identifier);
        return identifier;
    }

    @Override
    public Optional<PersonIdentifierEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        PersonIdentifierEntity entity = store.get(key(context.getTenantId(), id));
        if (entity != null && entity.getDeletedAt() == null) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    public List<PersonIdentifierEntity> findByPersonId(UserSecurityContext context, UUID personId) {
        if (personId == null) return Collections.emptyList();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(i -> tenantId.equals(i.getTenantId()) && personId.equals(i.getPersonId()) && i.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PersonIdentifierEntity> findByPersonIdAndType(UserSecurityContext context, UUID personId, String idType) {
        if (personId == null || idType == null) return Optional.empty();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(i -> tenantId.equals(i.getTenantId())
                        && personId.equals(i.getPersonId())
                        && idType.equalsIgnoreCase(i.getIdType())
                        && i.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public boolean existsByTypeAndValue(UserSecurityContext context, String idType, String rawValue) {
        if (idType == null || rawValue == null) return false;
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .anyMatch(i -> tenantId.equals(i.getTenantId())
                        && idType.equalsIgnoreCase(i.getIdType())
                        && rawValue.trim().equalsIgnoreCase(i.getRawValue())
                        && i.getDeletedAt() == null);
    }
}
