package org.codeberg.edryal.ledger.bookkeeping.domain;

public enum AccountCategory {

    ASSET(Direction.DEBIT),
    LIABILITY(Direction.CREDIT),
    REVENUE(Direction.CREDIT);

    private final Direction normalSide;

    AccountCategory(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }
}
