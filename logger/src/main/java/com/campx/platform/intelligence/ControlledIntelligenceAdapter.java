package com.campx.platform.intelligence;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controlled Adapter / Test Double for the CampXSync Intelligence Layer.
 * <p>
 * Captures, verifies, and records emitted domain events. Validates contract invariants,
 * tenant context, and least-privilege compliance. Explicitly marks the live Intelligence
 * Layer endpoint integration as PENDING until downstream service deployment.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 1.0.0
 * @since 2.0.0
 */
public class ControlledIntelligenceAdapter {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ControlledIntelligenceAdapter.class);

    private final List<IntelligenceContextEvent> publishedEvents = new CopyOnWriteArrayList<>();
    private volatile boolean liveEndpointAvailable = false;

    /**
     * Publishes an event to the Intelligence Layer adapter, strictly enforcing contract invariants.
     *
     * @param event the event to emit
     */
    public void publish(IntelligenceContextEvent event) {
        IntelligenceContractValidator.validate(event);
        publishedEvents.add(event);
        logger.info("[INTELLIGENCE_LAYER_EMIT] Published event [{}] type={} tenant={} liveEndpoint={}",
                event.getEventId(), event.getEventType(), event.getTenantId(), liveEndpointAvailable);
    }

    /**
     * @return unmodifiable view of all events emitted to this adapter
     */
    public List<IntelligenceContextEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    /**
     * Alias for {@link #getPublishedEvents()} for test and verification harnesses.
     */
    public List<IntelligenceContextEvent> getCapturedEvents() {
        return getPublishedEvents();
    }

    /**
     * @return count of captured events in memory
     */
    public int getCapturedCount() {
        return publishedEvents.size();
    }

    /**
     * Finds published events matching a specific tenant ID.
     */
    public List<IntelligenceContextEvent> getPublishedEventsForTenant(String tenantId) {
        if (tenantId == null) return Collections.emptyList();
        List<IntelligenceContextEvent> list = new java.util.ArrayList<>();
        for (IntelligenceContextEvent e : publishedEvents) {
            if (tenantId.equals(e.getTenantId())) {
                list.add(e);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Clears all recorded events from memory.
     */
    public void clear() {
        publishedEvents.clear();
    }

    public boolean isLiveEndpointAvailable() {
        return liveEndpointAvailable;
    }

    public void setLiveEndpointAvailable(boolean liveEndpointAvailable) {
        this.liveEndpointAvailable = liveEndpointAvailable;
    }
}
