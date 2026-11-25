package org.codeberg.edryal.ledger.transfers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.PostEntry;
import org.codeberg.edryal.ledger.bookkeeping.PostResult;
import org.codeberg.edryal.ledger.bookkeeping.Posting;
import org.codeberg.edryal.ledger.bookkeeping.SystemAccount;
import org.codeberg.edryal.ledger.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TransferService {

    private final Ledger ledger;
    private final TransferRepository transfers;

    TransferService(Ledger ledger, TransferRepository transfers) {
        this.ledger = ledger;
        this.transfers = transfers;
    }

    Transfer deposit(AccountId account, Money amount, String description) {
        AccountId cash = ledger.systemAccount(SystemAccount.CASH, amount.currency());
        return execute(TransferKind.DEPOSIT, Optional.empty(), Optional.of(account), amount, description,
                List.of(Posting.debit(cash, amount), Posting.credit(account, amount)));
    }

    Transfer withdraw(AccountId account, Money amount, String description) {
        AccountId cash = ledger.systemAccount(SystemAccount.CASH, amount.currency());
        return execute(TransferKind.WITHDRAWAL, Optional.of(account), Optional.empty(), amount, description,
                List.of(Posting.debit(account, amount), Posting.credit(cash, amount)));
    }

    Transfer transfer(AccountId source, AccountId destination, Money amount, String description) {
        if (source.equals(destination)) {
            throw new SameAccountException(source);
        }

        return execute(TransferKind.TRANSFER, Optional.of(source), Optional.of(destination), amount, description,
                List.of(Posting.debit(source, amount), Posting.credit(destination, amount)));
    }

    @Transactional(readOnly = true)
    Transfer get(TransferId id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }

    private Transfer execute(TransferKind kind, Optional<AccountId> source, Optional<AccountId> destination, Money amount, String description, List<Posting> postings) {
        TransferId id = TransferId.newId();
        var entry = new PostEntry("transfer:" + id, description, postings);

        return switch (ledger.post(entry)) {
            case PostResult.Posted posted -> {
                var transfer = new Transfer(id, kind, source, destination, amount, description, posted.entryId(),
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
                transfers.insert(transfer);
                yield transfer;
            }
            case PostResult.Rejected rejected -> throw new InsufficientFundsException(rejected.accountId(),
                    ledger.balanceOf(rejected.accountId()), amount);
        };
    }
}
