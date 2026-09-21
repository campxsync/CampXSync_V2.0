package com.campx.logger.api;

import java.util.UUID;

/**
 * AutoCloseable execution flow tracker for tracing execution path,
 * measuring operation latency, and identifying performance bottlenecks in CampXSync services.
 */
public class FlowTracker implements AutoCloseable {
    private final ILogger logger;
    private final String operationName;
    private final String flowId;
    private final long startTimeMs;
    private long lastStepTimeMs;
    private boolean closed = false;
    private boolean failed = false;
    private Throwable failureCause = null;

    public FlowTracker(ILogger logger, String operationName, String flowId) {
        this.logger = logger;
        this.operationName = operationName != null ? operationName : "ANONYMOUS_FLOW";
        this.flowId = flowId != null ? flowId : UUID.randomUUID().toString();
        this.startTimeMs = System.currentTimeMillis();
        this.lastStepTimeMs = this.startTimeMs;

        // Log entry
        this.logger.log(LogEvent.builder()
                .level(LogLevel.DEBUG)
                .loggerName(logger.getName())
                .operation(this.operationName)
                .flowId(this.flowId)
                .addTag("FLOW_START")
                .message("START FLOW [" + this.operationName + "] (flowId=" + this.flowId + ")")
                .build());
    }

    public String getFlowId() {
        return flowId;
    }

    public String getOperationName() {
        return operationName;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Records a milestone or sub-step in the execution flow.
     */
    public FlowTracker step(String stepName) {
        long now = System.currentTimeMillis();
        long stepDuration = now - lastStepTimeMs;
        long totalElapsed = now - startTimeMs;
        lastStepTimeMs = now;

        this.logger.log(LogEvent.builder()
                .level(LogLevel.DEBUG)
                .loggerName(logger.getName())
                .operation(this.operationName)
                .flowId(this.flowId)
                .durationMs(stepDuration)
                .addTag("FLOW_STEP")
                .message("STEP [" + this.operationName + " -> " + stepName + "] stepDuration=" + stepDuration + "ms, totalElapsed=" + totalElapsed + "ms")
                .build());

        return this;
    }

    /**
     * Marks the flow as failed with an exception.
     */
    public FlowTracker markFailed(Throwable t) {
        this.failed = true;
        this.failureCause = t;
        return this;
    }

    /**
     * Closes the flow, logs completion metrics and execution latency.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        long totalDurationMs = System.currentTimeMillis() - startTimeMs;

        if (failed || failureCause != null) {
            this.logger.log(LogEvent.builder()
                    .level(LogLevel.ERROR)
                    .loggerName(logger.getName())
                    .operation(this.operationName)
                    .flowId(this.flowId)
                    .durationMs(totalDurationMs)
                    .throwable(failureCause)
                    .addTag("FLOW_FAILURE")
                    .message("FAILED FLOW [" + this.operationName + "] failed after " + totalDurationMs + "ms: " + (failureCause != null ? failureCause.getMessage() : "Unknown error"))
                    .build());
        } else {
            this.logger.log(LogEvent.builder()
                    .level(LogLevel.INFO)
                    .loggerName(logger.getName())
                    .operation(this.operationName)
                    .flowId(this.flowId)
                    .durationMs(totalDurationMs)
                    .addTag("FLOW_SUCCESS")
                    .message("COMPLETED FLOW [" + this.operationName + "] executed in " + totalDurationMs + "ms")
                    .build());
        }
    }
}
