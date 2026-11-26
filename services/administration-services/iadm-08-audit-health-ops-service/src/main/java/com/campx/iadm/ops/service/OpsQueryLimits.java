package com.campx.iadm.ops.service;

/**
 * DEMO ONLY configurable query bounds (IADM-08-US-020 / decision D4).
 * Plain language: time window and page size caps; over-max window is rejected, never truncated.
 */
public final class OpsQueryLimits {

    /** DEMO ONLY default maximum look-back window in days. */
    public static final int DEFAULT_MAX_WINDOW_DAYS = 31;
    /** DEMO ONLY default page size. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    private static volatile int maxWindowDays = DEFAULT_MAX_WINDOW_DAYS;
    private static volatile int pageSize = DEFAULT_PAGE_SIZE;

    private OpsQueryLimits() {
    }

    public static int getMaxWindowDays() {
        return maxWindowDays;
    }

    public static int getPageSize() {
        return pageSize;
    }

    public static void setMaxWindowDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("maxWindowDays must be >= 1");
        }
        maxWindowDays = days;
    }

    public static void setPageSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        pageSize = size;
    }

    public static void resetDefaults() {
        maxWindowDays = DEFAULT_MAX_WINDOW_DAYS;
        pageSize = DEFAULT_PAGE_SIZE;
    }
}
