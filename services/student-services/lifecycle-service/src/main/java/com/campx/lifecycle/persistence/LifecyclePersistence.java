package com.campx.lifecycle.persistence;

import com.campx.lifecycle.Actor;
import com.campx.lifecycle.LifecycleDomainService.GraduationRecord;
import com.campx.lifecycle.LifecycleDomainService.HistoryEntry;
import com.campx.lifecycle.LifecycleDomainService.LifecycleRequest;
import com.campx.lifecycle.LifecycleDomainService.LifecycleStatus;
import com.campx.lifecycle.LifecycleDomainService.TransitionRule;

import java.util.List;
import java.util.UUID;

/**
 * Committed PostgreSQL access for STM-04 lifecycle tables.
 * Implementations must keep the caller's tenant on every read and write.
 * Unit tests run without an implementation and use the in-memory domain only.
 */
public interface LifecyclePersistence {

    void saveStatus(Actor actor, LifecycleStatus status);

    void appendHistory(Actor actor, HistoryEntry entry);

    void saveRequest(Actor actor, LifecycleRequest request);

    void saveRule(Actor actor, TransitionRule rule);

    void saveGraduation(Actor actor, GraduationRecord record);

    LifecycleStatus findStatus(Actor actor, UUID studentId);

    List<HistoryEntry> findHistory(Actor actor, UUID studentId);

    LifecycleRequest findRequest(Actor actor, UUID requestId);

    List<LifecycleRequest> findPendingRequests(Actor actor);

    List<LifecycleRequest> findRequestsByStudent(Actor actor, UUID studentId);

    List<LifecycleStatus> findAllStatuses(Actor actor);

    List<UUID> findAdviseesByAdvisor(Actor actor, UUID advisorUserId);
}
