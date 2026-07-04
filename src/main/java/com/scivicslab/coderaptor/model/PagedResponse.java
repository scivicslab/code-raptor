package com.scivicslab.coderaptor.model;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param total      total number of items matching the query (across all pages)
 * @param page       0-based current page index
 * @param size       page size requested
 * @param totalPages number of pages (ceil(total / size))
 * @param items      items on this page
 */
public record PagedResponse<T>(
        int total,
        int page,
        int size,
        int totalPages,
        List<T> items
) {}
