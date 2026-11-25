package org.codeberg.edryal.ledger.bookkeeping.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.codeberg.edryal.ledger.bookkeeping.Direction;
import org.junit.jupiter.api.Test;

class AccountCategoryTest {

    @Test
    void normalSides() {
        assertThat(AccountCategory.ASSET.normalSide()).isEqualTo(Direction.DEBIT);
        assertThat(AccountCategory.LIABILITY.normalSide()).isEqualTo(Direction.CREDIT);
        assertThat(AccountCategory.REVENUE.normalSide()).isEqualTo(Direction.CREDIT);
    }
}
