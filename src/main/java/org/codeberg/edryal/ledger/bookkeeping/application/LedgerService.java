package org.codeberg.edryal.ledger.bookkeeping.application;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.OpenAccount;
import org.codeberg.edryal.ledger.bookkeeping.domain.Account;
import org.codeberg.edryal.ledger.bookkeeping.domain.AccountCategory;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.AccountRepository;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class LedgerService implements Ledger {

    private final AccountRepository accounts;

    LedgerService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public AccountId open(OpenAccount command) {
        var account = new Account(AccountId.newId(), categoryOf(command.type()), command.currency(), false);
        accounts.insert(account, command.type(), command.ownerId());
        return account.id();
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceOf(AccountId id) {
        return accounts.findBalance(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static AccountCategory categoryOf(AccountType type) {
        return switch (type) {
            case CUSTOMER_WALLET, MERCHANT_BALANCE -> AccountCategory.LIABILITY;
        };
    }
}
