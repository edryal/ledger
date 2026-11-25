package org.codeberg.edryal.ledger.bookkeeping.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;

public record JournalEntry(JournalEntryId id,
                           String reference,
                           String description,
                           Instant postedAt,
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
                throw new IllegalArgumentException("entry is unbalanced in " + currency + " by " + net +
                        " minor units");
            }
        });
    }
}
