package org.codeberg.edryal.ledger.bookkeeping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.common.Money;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private final AccountId holds = AccountId.newId();
    private final AccountId merchant = AccountId.newId();
    private final AccountId fees = AccountId.newId();

    private static Money eur(long cents) {
        return Money.of(cents, "EUR");
    }

    private static JournalEntry entry(List<Posting> postings) {
        return new JournalEntry(JournalEntryId.newId(), "test:1", "test", Instant.now(), postings);
    }

    @Test
    void acceptsBalancedCaptureFromReadme() {
        var capture = entry(List.of(
                Posting.debit(holds, eur(2599)),
                Posting.credit(merchant, eur(2538)),
                Posting.credit(fees, eur(61))));

        assertThat(capture.postings()).hasSize(3);
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> entry(List.of(
                Posting.debit(holds, eur(2599)),
                Posting.credit(merchant, eur(2538)),
                Posting.credit(fees, eur(60)))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("unbalanced");
    }

    @Test
    void rejectsSinglePosting() {
        assertThatThrownBy(() -> entry(List.of(Posting.debit(holds, eur(100)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 postings");
    }

    @Test
    void rejectsBlankReference() {
        var postings = List.of(Posting.debit(holds, eur(100)), Posting.credit(merchant, eur(100)));

        assertThatThrownBy(() -> new JournalEntry(
                JournalEntryId.newId(), "  ", "test", Instant.now(), postings))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("reference");
    }

    @Test
    void rejectsEntryBalancedOnlyAcrossCurrencies() {
        // +1000 EUR and -1000 USD sum to 0 as numbers, but no currency balances on its own
        assertThatThrownBy(() -> entry(List.of(
                Posting.debit(holds, eur(1000)),
                Posting.credit(merchant, Money.of(1000, "USD")))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("unbalanced");
    }

    @Test
    void acceptsEntryBalancedInEachCurrency() {
        var entry = entry(List.of(
                Posting.debit(holds, eur(1000)),
                Posting.credit(merchant, eur(1000)),
                Posting.debit(fees, Money.of(500, "USD")),
                Posting.credit(holds, Money.of(500, "USD"))));

        assertThat(entry.postings()).hasSize(4);
    }

    @Test
    void isNotAffectedByChangesToTheOriginalList() {
        var postings = new ArrayList<>(List.of(
                Posting.debit(holds, eur(100)),
                Posting.credit(merchant, eur(100))));
        var entry = entry(postings);

        postings.add(Posting.debit(fees, eur(999)));

        assertThat(entry.postings()).hasSize(2);
    }

    @Test
    void postingsCannotBeModified() {
        var entry = entry(List.of(
                Posting.debit(holds, eur(100)),
                Posting.credit(merchant, eur(100))));

        assertThatThrownBy(() -> entry.postings().add(Posting.debit(fees, eur(1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
