package org.codeberg.edryal.ledger.bookkeeping.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.bookkeeping.Posting;

/**
 * One atomic business event in the ledger, such as a transfer or a payment capture.
 *
 * <p>Invariants, checked on construction:
 * <ul>
 * <li>at least two postings</li>
 * <li>for each currency, debits equal credits</li>
 * <li>a non-blank reference that identifies the business event (e.g. {@code transfer:<id>})</li>
 * </ul>
 *
 * <p>Entries are immutable and are never changed after posting. Mistakes are corrected with a reversal entry.
 */
public record JournalEntry(JournalEntryId id, String reference, String description, Instant postedAt,
                           List<Posting> postings) {

    public JournalEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
        Objects.requireNonNull(postings, "postings must not be null");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }

        // immutable copy, which also rejects null elements
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new IllegalArgumentException("journal entry needs at least 2 postings, got " + postings.size());
        }

        requireBalancedPerCurrency(postings);
    }

    private static void requireBalancedPerCurrency(List<Posting> postings) {
        Map<Currency, Long> netByCurrency = postings.stream()
                .collect(Collectors.groupingBy(p -> p.amount().currency(),
                        Collectors.reducing(0L, Posting::signedMinorUnits, Math::addExact)));

        netByCurrency.forEach((currency, net) -> {
            if (net != 0) {
                throw new IllegalArgumentException(
                        "entry is unbalanced in " + currency + " by " + net + " minor units");
            }
        });
    }
}
