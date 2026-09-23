package com.campx.academic.course.exception;

/**
 * Thrown when a course deactivation is blocked because active batch offerings exist (HTTP 422 Unprocessable Entity).
 */
public class ActiveBatchOfferingsException extends CourseException {

    /**
     * Constructs a new {@code ActiveBatchOfferingsException} indicating the blocking course code and active batch count.
     *
     * @param courseCode       the course code attempting deactivation
     * @param activeBatchCount the number of active batch offerings still registered
     */
    public ActiveBatchOfferingsException(String courseCode, int activeBatchCount) {
        super(422, "ACD_ACTIVE_BATCH_EXISTS",
                "Course [" + courseCode + "] cannot be deactivated because " + activeBatchCount
                        + " active batch offering(s) still exist. Close batch offerings before deactivation.");
    }
}
