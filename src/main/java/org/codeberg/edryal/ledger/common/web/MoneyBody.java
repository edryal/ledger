package org.codeberg.edryal.ledger.common.web;

import jakarta.validation.constraints.NotNull;
import java.util.Currency;
import org.codeberg.edryal.ledger.common.Money;

/**
 * How money looks in JSON: {@code {"value": 2599, "currency": "EUR"}}.
 * The value is in minor units, so clients never parse decimals.
 */
public record MoneyBody(long value, @NotNull Currency currency) {

    public static MoneyBody from(Money money) {
        return new MoneyBody(money.minorUnits(), money.currency());
    }

    public Money toMoney() {
        return Money.of(value, currency);
    }
}
