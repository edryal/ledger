package org.codeberg.edryal.ledger.bookkeeping.infrastructure;

import org.codeberg.edryal.ledger.bookkeeping.domain.Account;

/** An account together with its current balance in minor units. */
public record AccountBalance(Account account, long balance) {
}
