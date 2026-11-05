package dev.jordy.jordylab.mcp;

import java.util.List;

/**
 * The one page envelope every list-returning connector tool uses (FR-009, data-model.md). The page size is clamped here,
 * so no tool can return more than {@link #MAX_ITEMS_PER_CALL} items.
 */
public record McpToolPage<T>(List<T> items, int returned, long totalMatching, boolean hasMore) {

    public static final int MAX_ITEMS_PER_CALL = 50;

    public static int clampLimit(int requested) {
        return Math.max(1, Math.min(requested, MAX_ITEMS_PER_CALL));
    }

    public static <T> McpToolPage<T> of(List<T> items, long totalMatching, int offset) {
        int returned = items.size();
        boolean hasMore = offset + returned < totalMatching;
        return new McpToolPage<>(items, returned, totalMatching, hasMore);
    }

    /** The first page of a list that is already in memory. */
    public static <T> McpToolPage<T> firstPage(List<T> all) {
        List<T> items = all.stream().limit(MAX_ITEMS_PER_CALL).toList();
        return new McpToolPage<>(items, items.size(), all.size(), all.size() > items.size());
    }
}
