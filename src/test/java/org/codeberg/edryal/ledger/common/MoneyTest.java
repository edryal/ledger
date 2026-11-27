package org.codeberg.edryal.ledger.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;

import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsUnknownCurrencyCode() {
        assertThatThrownBy(() -> Money.of(100, "XYZ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCurrencyWithoutMinorUnit() {
        assertThatThrownBy(() -> Money.of(100, "XXX")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no minor unit");
    }

    @Test
    void addsSameCurrency() {
        assertThat(Money.of(2599, "EUR").plus(Money.of(100, "EUR"))).isEqualTo(Money.of(2699, "EUR"));
    }

    @Test
    void subtractingCanGoNegative() {
        assertThat(Money.of(100, "EUR").minus(Money.of(250, "EUR"))).isEqualTo(Money.of(-150, "EUR"));
    }

    @Test
    void rejectsMixedCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "EUR").plus(Money.of(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }

    @Test
    void detectsOverflow() {
        Money max = Money.of(Long.MAX_VALUE, EUR);
        assertThatThrownBy(() -> max.plus(Money.of(1, EUR))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void equalAmountsAreEqual() {
        Money a = Money.of(2599, "EUR");
        Money b = Money.of(2599, EUR);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void formatsUsingCurrencyFractionDigits() {
        assertThat(Money.of(2599, "EUR")).hasToString("25.99 EUR");
        assertThat(Money.of(5, "EUR")).hasToString("0.05 EUR");
        assertThat(Money.of(-150, "EUR")).hasToString("-1.50 EUR");
        assertThat(Money.of(500, "JPY")).hasToString("500 JPY");
    }

    @Test
    void negateFlipsTheSign() {
        assertThat(Money.of(2599, "EUR").negate()).isEqualTo(Money.of(-2599, "EUR"));
    }

    @Test
    void zeroIsZero() {
        assertThat(Money.zero(Currency.getInstance("EUR")).isZero()).isTrue();
        assertThat(Money.of(1, "EUR").isZero()).isFalse();
    }
}
