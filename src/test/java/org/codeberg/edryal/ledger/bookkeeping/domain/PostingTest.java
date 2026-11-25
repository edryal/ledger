package org.codeberg.edryal.ledger.bookkeeping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.common.Money;
import org.junit.jupiter.api.Test;

class PostingTest {

    private final AccountId account = AccountId.newId();

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> Posting.debit(account, Money.of(0, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Posting.credit(account, Money.of(-100, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void debitCountsPositiveAndCreditNegative() {
        assertThat(Posting.debit(account, Money.of(2599, "EUR")).signedMinorUnits()).isEqualTo(2599);
        assertThat(Posting.credit(account, Money.of(2599, "EUR")).signedMinorUnits()).isEqualTo(-2599);
    }
}
