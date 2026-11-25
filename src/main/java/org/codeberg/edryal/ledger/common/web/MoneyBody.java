package org.codeberg.edryal.ledger.common.web;

import java.util.Currency;

import org.codeberg.edryal.ledger.common.Money;

import jakarta.validation.constraints.NotNull;

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
