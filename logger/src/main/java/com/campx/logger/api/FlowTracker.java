package com.campx.logger.api;

import java.util.UUID;

/**
 * AutoCloseable execution flow tracker for tracing execution paths, measuring operation latency,
 * and identifying performance bottlenecks in CampXSync distributed microservices.
 * <p>
 * Implements {@link AutoCloseable} to enable clean usage via try-with-resources blocks:
 * <pre>{@code
 * try (FlowTracker tracker = logger.flow("processEnrollment")) {
 *     tracker.step("validateRequest");
 *     // perform validation
 *     tracker.step("persistDatabase");
 *     // perform DB insert
 * } catch (Exception e) {
 *     // tracker automatically records failure duration upon close
 * }
 * }</pre>
 *
 * @see ILogger#flow(String)
 * @see ILogger#flow(String, String)
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

    /**
     * Initializes a new execution flow tracker and logs the flow commencement at {@code DEBUG} level.
     *
     * @param logger        the logger instance used to dispatch tracking events
     * @param operationName the high-level business or system operation name being traced
     * @param flowId        the unique identifier for correlating log events in this flow, or {@code null} to auto-generate a UUID
     */
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

    /**
     * Returns the unique flow identifier associated with this tracker.
     *
     * @return the flow UUID or custom tracking key
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Returns the business operation name assigned to this flow.
     *
     * @return the operation name
     */
    public String getOperationName() {
        return operationName;
    }

    /**
     * Returns the millisecond timestamp when this flow was initialized.
     *
     * @return the epoch time in milliseconds
     */
    public long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * Calculates the elapsed time in milliseconds since the start of this flow.
     *
     * @return the total elapsed duration in milliseconds
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Records a milestone or sub-step in the execution flow, logging step duration and total elapsed time.
     *
     * @param stepName the name of the milestone or sub-step reached
     * @return this tracker instance for method chaining
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
     * Marks the flow as failed with the specified root cause exception.
     *
     * @param t the throwable representing the failure
     * @return this tracker instance for method chaining
     */
    public FlowTracker markFailed(Throwable t) {
        this.failed = true;
        this.failureCause = t;
        return this;
    }

    /**
     * Closes the flow, logging completion metrics, execution latency, and success/failure status.
     * Invoked automatically when used in a try-with-resources block.
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
