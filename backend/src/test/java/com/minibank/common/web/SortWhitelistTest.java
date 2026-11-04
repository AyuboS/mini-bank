package com.minibank.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class SortWhitelistTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "amount");
    private static final Sort FALLBACK = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void keepsAllowedSortProperties() {
        var result = SortWhitelist.sanitize(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "amount")), ALLOWED, FALLBACK);

        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "amount"));
    }

    @Test
    void dropsPropertiesThatAreNotPartOfThePublicContract() {
        var result = SortWhitelist.sanitize(
                PageRequest.of(0, 20, Sort.by("passwordHash")), ALLOWED, FALLBACK);

        assertThat(result.getSort()).isEqualTo(FALLBACK);
    }

    @Test
    void keepsTheAllowedPartOfAMixedSort() {
        var result = SortWhitelist.sanitize(
                PageRequest.of(0, 20, Sort.by("amount").and(Sort.by("passwordHash"))), ALLOWED, FALLBACK);

        assertThat(result.getSort()).isEqualTo(Sort.by("amount"));
    }

    @Test
    void clampsThePageSize() {
        var result = SortWhitelist.sanitize(PageRequest.of(0, 5000), ALLOWED, FALLBACK);

        assertThat(result.getPageSize()).isEqualTo(SortWhitelist.MAX_PAGE_SIZE);
    }

    @Test
    void preservesThePageNumber() {
        var result = SortWhitelist.sanitize(PageRequest.of(3, 20), ALLOWED, FALLBACK);

        assertThat(result.getPageNumber()).isEqualTo(3);
    }
}
