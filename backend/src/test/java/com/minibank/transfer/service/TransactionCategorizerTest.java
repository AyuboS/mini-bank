package com.minibank.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.TransferType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The description heuristic, and the limits it is deliberately kept inside. */
@DisplayName("Transaction categoriser")
class TransactionCategorizerTest {

    private final TransactionCategorizer categorizer = new TransactionCategorizer();

    @ParameterizedTest
    @CsvSource({
        "Netflix August, ENTERTAINMENT",
        "Grocery store, GROCERIES",
        "Rent for September, BILLS",
        "Taxi home, TRANSPORT",
        "Dinner with friends, DINING",
        "Flight to Bangkok, TRAVEL",
        "August salary, SALARY",
        "Pharmacy, HEALTH",
        "University tuition, EDUCATION",
    })
    @DisplayName("places a payment from a word in its description")
    void placesAPaymentFromItsDescription(String description, TransactionCategory expected) {
        assertThat(categorizer.fromDescription(description)).contains(expected);
    }

    @Nested
    @DisplayName("matches whole words only")
    class WholeWords {

        @Test
        @DisplayName("does not read 'car' out of 'carbon'")
        void doesNotMatchInsideAWord() {
            // Substring matching would file this under Transport, and a customer who sees that
            // stops trusting every other row on the page.
            assertThat(categorizer.fromDescription("Carbon offset")).isEmpty();
        }

        @Test
        @DisplayName("does not read 'bill' out of 'billing'")
        void doesNotMatchAPrefix() {
            assertThat(categorizer.fromDescription("Billington Ltd")).isEmpty();
        }

        @Test
        @DisplayName("splits on punctuation, so 'Netflix-August' still matches")
        void splitsOnPunctuation() {
            assertThat(categorizer.fromDescription("Netflix-August")).contains(TransactionCategory.ENTERTAINMENT);
        }
    }

    @Test
    @DisplayName("leaves an unplaceable payment uncategorised rather than guessing")
    void declinesToGuess() {
        // An honest blank the customer can see and correct beats a confident wrong answer.
        assertThat(categorizer.categorize(null, "Payment to ACME Holdings", TransferType.INTERNAL))
                .isEmpty();
        assertThat(categorizer.fromDescription(null)).isEmpty();
        assertThat(categorizer.fromDescription("   ")).isEmpty();
    }

    @Test
    @DisplayName("never overrides a category the customer chose")
    void keepsAnExplicitChoice() {
        // The description says groceries; the customer says shopping. The customer wins.
        assertThat(categorizer.categorize(TransactionCategory.SHOPPING, "Grocery store", TransferType.INTERNAL))
                .contains(TransactionCategory.SHOPPING);
    }

    @Test
    @DisplayName("falls back on the movement's kind when the text says nothing")
    void usesTheMovementKindAsAFallback() {
        assertThat(categorizer.categorize(null, null, TransferType.DEPOSIT))
                .contains(TransactionCategory.TRANSFER);
        assertThat(categorizer.categorize(null, null, TransferType.WITHDRAWAL))
                .contains(TransactionCategory.TRANSFER);
        // An internal transfer with no description could be anything, so it stays blank.
        assertThat(categorizer.categorize(null, null, TransferType.INTERNAL)).isEmpty();
    }

    @Test
    @DisplayName("consults the more specific category first")
    void prefersTheMoreSpecificCategory() {
        // "train" belongs to Transport and would also read as travel; the ordering decides.
        assertThat(categorizer.fromDescription("Train ticket")).contains(TransactionCategory.TRANSPORT);
    }

    @Test
    @DisplayName("is case insensitive")
    void ignoresCase() {
        assertThat(categorizer.fromDescription("NETFLIX")).contains(TransactionCategory.ENTERTAINMENT);
        assertThat(categorizer.fromDescription("netflix")).contains(TransactionCategory.ENTERTAINMENT);
    }
}
