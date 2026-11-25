package org.codeberg.edryal.ledger.common.web;

import java.util.Currency;

import org.codeberg.edryal.ledger.common.Money;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * An amount sent in a request, e.g. {@code {"value": 2500, "currency": "EUR"}}.
 * Always positive. The operation (deposit, transfer, ...) decides which way the money moves.
 */
public record AmountBody(@Positive long value, @NotNull Currency currency) {

    public Money toMoney() {
        return Money.of(value, currency);
    }
}
