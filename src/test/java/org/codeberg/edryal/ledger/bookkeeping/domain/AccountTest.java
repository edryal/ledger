package org.codeberg.edryal.ledger.bookkeeping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.common.Money;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private final Account wallet = new Account(AccountId.newId(), AccountCategory.LIABILITY, EUR, false);
    private final Account cash = new Account(AccountId.newId(), AccountCategory.ASSET, EUR, true);

    private static Money eur(long cents) {
        return Money.of(cents, "EUR");
    }

    @Test
    void creditIncreasesLiability() {
        // deposit - Cr Catalin wallet 100.00
        assertThat(wallet.balanceChange(Posting.credit(wallet.id(), eur(10000)))).isEqualTo(10000);
    }

    @Test
    void debitDecreasesLiability() {
        // authorize - Dr Catalin wallet 25.99
        assertThat(wallet.balanceChange(Posting.debit(wallet.id(), eur(2599)))).isEqualTo(-2599);
    }

    @Test
    void debitIncreasesAsset() {
        // deposit - Dr Platform cash 100.00
        assertThat(cash.balanceChange(Posting.debit(cash.id(), eur(10000)))).isEqualTo(10000);
    }

    @Test
    void rejectsPostingForAnotherAccount() {
        assertThatThrownBy(() -> wallet.balanceChange(Posting.credit(cash.id(), eur(100))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPostingInAnotherCurrency() {
        assertThatThrownBy(() -> wallet.balanceChange(Posting.credit(wallet.id(), Money.of(100, "USD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void walletCannotGoNegative() {
        assertThat(wallet.permits(0)).isTrue();
        assertThat(wallet.permits(-1)).isFalse();
    }

    @Test
    void accountAllowingNegativeAcceptsAnyBalance() {
        assertThat(cash.permits(-1)).isTrue();
    }

    @Test
    void depositIncreasesBothSides() {
        // Dr Platform cash 100.00
        long cashChange = cash.balanceChange(Posting.debit(cash.id(), eur(10000)));

        // Cr Catalin wallet 100.00
        long walletChange = wallet.balanceChange(Posting.credit(wallet.id(), eur(10000)));

        assertThat(cashChange).isEqualTo(10000);
        assertThat(walletChange).isEqualTo(10000);
    }
}
