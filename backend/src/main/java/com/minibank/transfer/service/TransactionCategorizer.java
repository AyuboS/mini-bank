package com.minibank.transfer.service;

import com.minibank.transfer.domain.TransactionCategory;
import com.minibank.transfer.domain.TransferType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Guesses a category from what the customer typed in the description.
 *
 * <p>This is a heuristic and is treated as one everywhere it is used. It runs only when no category
 * was supplied, it never overwrites a choice the customer made, and a payment it cannot place is
 * left uncategorised rather than dropped into {@code OTHER} — an honest blank is more useful than a
 * confident wrong answer, because the customer can see the blank and fix it.
 *
 * <p>Matching is on whole words. Substring matching sounds more forgiving and is worse: "carbon"
 * contains "car", "billing" contains "bill", and a customer who sees rent filed under Transport
 * stops trusting the analytics entirely.
 */
@Component
public class TransactionCategorizer {

    /**
     * Keyword table, most specific first.
     *
     * <p>Order matters: "train ticket" should land on Transport rather than on Travel, so Transport
     * is consulted first. A {@link LinkedHashMap} makes that ordering part of the declaration
     * instead of an accident of iteration.
     */
    private static final Map<TransactionCategory, List<String>> KEYWORDS = buildKeywords();

    private static Map<TransactionCategory, List<String>> buildKeywords() {
        Map<TransactionCategory, List<String>> map = new LinkedHashMap<>();
        map.put(TransactionCategory.SALARY, List.of("salary", "payroll", "wage", "wages", "stipend", "bonus"));
        map.put(TransactionCategory.GROCERIES, List.of(
                "grocery", "groceries", "supermarket", "market", "aldi", "lidl", "rimi", "maxima"));
        map.put(TransactionCategory.DINING, List.of(
                "restaurant", "cafe", "coffee", "lunch", "dinner", "breakfast", "bar", "pizza", "sushi",
                "takeaway", "bakery"));
        map.put(TransactionCategory.TRANSPORT, List.of(
                "taxi", "uber", "bolt", "fuel", "petrol", "diesel", "parking", "metro", "bus", "tram",
                "train", "transport", "toll"));
        map.put(TransactionCategory.BILLS, List.of(
                "rent", "electricity", "gas", "water", "internet", "broadband", "phone", "mobile",
                "utility", "utilities", "insurance", "council", "bill"));
        map.put(TransactionCategory.ENTERTAINMENT, List.of(
                "netflix", "spotify", "cinema", "movie", "game", "games", "concert", "subscription",
                "streaming", "theatre"));
        map.put(TransactionCategory.TRAVEL, List.of(
                "flight", "airline", "hotel", "hostel", "booking", "airbnb", "holiday", "vacation", "trip"));
        map.put(TransactionCategory.HEALTH, List.of(
                "pharmacy", "doctor", "dentist", "clinic", "hospital", "gym", "fitness", "medical"));
        map.put(TransactionCategory.EDUCATION, List.of(
                "tuition", "course", "university", "school", "books", "textbook", "training"));
        map.put(TransactionCategory.SHOPPING, List.of(
                "amazon", "clothes", "clothing", "shoes", "electronics", "furniture", "shop", "store"));
        map.put(TransactionCategory.SAVINGS, List.of("savings", "saving", "goal", "deposit"));
        // Collections.unmodifiableMap, not Map.copyOf: Map.copyOf gives an unmodifiable map
        // with *unspecified* iteration order, which would silently discard the ordering this
        // table depends on.
        return Collections.unmodifiableMap(map);
    }

    /**
     * Suggests a category for a transfer.
     *
     * @param explicit what the customer chose, if anything. Returned unchanged when present.
     * @param description free text from the payment, which may be null
     * @param type the movement's kind; a deposit is income regardless of what the text says
     * @return a category, or empty when nothing can be said with confidence
     */
    public Optional<TransactionCategory> categorize(
            TransactionCategory explicit, String description, TransferType type) {

        if (explicit != null) {
            return Optional.of(explicit);
        }

        Optional<TransactionCategory> fromText = fromDescription(description);
        if (fromText.isPresent()) {
            return fromText;
        }

        // Nothing in the text. The movement's kind is still worth something: a deposit or a
        // withdrawal is structurally a transfer of funds, not a purchase.
        return switch (type) {
            case DEPOSIT, WITHDRAWAL, REVERSAL -> Optional.of(TransactionCategory.TRANSFER);
            case INTERNAL -> Optional.empty();
        };
    }

    /** The keyword pass on its own, exposed for recategorising existing rows. */
    public Optional<TransactionCategory> fromDescription(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        // Split on anything that is not a letter or digit, so "Netflix-August" yields two words and
        // punctuation never has to be enumerated.
        List<String> words = List.of(description.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+"));

        for (Map.Entry<TransactionCategory, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (words.contains(keyword)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }
}
