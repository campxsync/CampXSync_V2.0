package com.campx.student.model;

import java.util.Collections;
import java.util.List;

/**
 * Generic container for cursor-paginated API responses.
 *
 * @param <T> Item payload type.
 */
public class CursorPagedResult<T> {

    private final List<T> items;
    private final String nextCursor;
    private final boolean hasMore;
    private final long totalCount;

    public CursorPagedResult(List<T> items, String nextCursor, boolean hasMore, long totalCount) {
        this.items = items != null ? items : Collections.emptyList();
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.totalCount = totalCount;
    }

    public List<T> getItems() { return items; }
    public String getNextCursor() { return nextCursor; }
    public boolean isHasMore() { return hasMore; }
    public long getTotalCount() { return totalCount; }
}
