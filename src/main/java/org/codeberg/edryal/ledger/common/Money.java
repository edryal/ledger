package org.codeberg.edryal.ledger.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of money in one currency.
 *
 * <p>The amount is stored in <b>minor units</b>, the smallest unit of the currency
 * (cents for EUR): {@code Money.of(2599, "EUR")} is 25.99 EUR, not 2599 EUR. The number
 * of decimals comes from the currency, so {@code Money.of(500, "JPY")} is 500 JPY.
 *
 * <p>Instances are immutable. Arithmetic returns new instances, and amounts can be negative.
 * Mixing currencies throws, and overflow throws instead of wrapping around.
 *
 * @param minorUnits the amount in minor units, e.g. cents
 * @param currency   the ISO 4217 currency
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("currency has no minor unit: " + currency.getCurrencyCode());
        }
    }

    /**
     * Creates an amount from minor units and an ISO 4217 code.
     *
     * @param minorUnits   the amount in minor units, e.g. {@code 2599} for 25.99 EUR
     * @param currencyCode an ISO 4217 code such as {@code "EUR"}
     * @throws IllegalArgumentException if the code is unknown or the currency has no minor unit
     */
    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    public static Money of(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }


    /**
     * Adds an amount of the same currency.
     *
     * @throws IllegalArgumentException if the currencies differ
     * @throws ArithmeticException      if the result overflows
     */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    @Override
    public String toString() {
        BigDecimal amount = BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits());
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
