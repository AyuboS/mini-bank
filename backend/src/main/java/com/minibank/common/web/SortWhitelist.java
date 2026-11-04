package com.minibank.common.web;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Restricts client-supplied sorting to a known set of properties.
 *
 * <p>Spring Data will happily sort by any entity property a client names, including ones that are
 * not meant to be part of the public contract, and an unknown name reaches the persistence layer as
 * an exception rather than a validation error. Whitelisting keeps both problems out.
 */
public final class SortWhitelist {

    /** Upper bound on page size, so a client cannot ask for the entire table in one request. */
    public static final int MAX_PAGE_SIZE = 100;

    private SortWhitelist() {}

    /**
     * Returns a pageable with unknown sort properties removed and the page size clamped.
     *
     * @param allowed property names a client may sort by
     * @param fallback ordering applied when nothing valid was requested
     */
    public static Pageable sanitize(Pageable pageable, Set<String> allowed, Sort fallback) {
        Sort filtered = Sort.by(pageable.getSort().stream()
                .filter(order -> allowed.contains(order.getProperty()))
                .toList());
        Sort effective = filtered.isSorted() ? filtered : fallback;
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size, effective);
    }
}
