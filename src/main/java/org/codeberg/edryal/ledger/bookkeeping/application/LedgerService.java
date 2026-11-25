package org.codeberg.edryal.ledger.bookkeeping.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountNotFoundException;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.JournalEntryId;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.OpenAccount;
import org.codeberg.edryal.ledger.bookkeeping.PostEntry;
import org.codeberg.edryal.ledger.bookkeeping.PostResult;
import org.codeberg.edryal.ledger.bookkeeping.Posting;
import org.codeberg.edryal.ledger.bookkeeping.RejectionReason;
import org.codeberg.edryal.ledger.bookkeeping.SystemAccount;
import org.codeberg.edryal.ledger.bookkeeping.domain.Account;
import org.codeberg.edryal.ledger.bookkeeping.domain.AccountCategory;
import org.codeberg.edryal.ledger.bookkeeping.domain.JournalEntry;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.AccountBalance;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.AccountRepository;
import org.codeberg.edryal.ledger.bookkeeping.infrastructure.JournalEntryRepository;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class LedgerService implements Ledger {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    LedgerService(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
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

    @Override
    @Transactional(readOnly = true)
    public AccountId systemAccount(SystemAccount account, Currency currency) {
        String code = account.code(currency);
        return accounts.findIdByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("no system account " + code));
    }

    @Override
    public PostResult post(PostEntry command) {
        // 1. Validate the entry on its own: >= 2 postings, balanced per currency.
        Instant postedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var entry = new JournalEntry(JournalEntryId.newId(), command.reference(), command.description(), postedAt,
                command.postings());

        // 2. Lock the balance rows of every account involved, in account ID order.
        var locked = accounts.lockBalances(entry.postings().stream().map(Posting::accountId).toList());

        // 3. Compute the new balances. Several postings can hit the same account.
        Map<AccountId, Long> newBalances = new LinkedHashMap<>();
        for (Posting posting : entry.postings()) {
            AccountBalance current = locked.get(posting.accountId());
            if (current == null) {
                throw new AccountNotFoundException(posting.accountId());
            }
            long before = newBalances.getOrDefault(posting.accountId(), current.balance());
            newBalances.put(posting.accountId(), Math.addExact(before, current.account().balanceChange(posting)));
        }

        // 4. Reject before writing anything if an account would go below what it permits.
        for (var change : newBalances.entrySet()) {
            Account account = locked.get(change.getKey()).account();
            if (!account.permits(change.getValue())) {
                return new PostResult.Rejected(RejectionReason.INSUFFICIENT_FUNDS, account.id());
            }
        }

        // 5. Write the entry, its postings and the new balances.
        entries.insert(entry);
        newBalances.forEach(accounts::updateBalance);
        return new PostResult.Posted(entry.id());
    }

    private static AccountCategory categoryOf(AccountType type) {
        return switch (type) {
            case CUSTOMER_WALLET, MERCHANT_BALANCE -> AccountCategory.LIABILITY;
        };
    }
}
