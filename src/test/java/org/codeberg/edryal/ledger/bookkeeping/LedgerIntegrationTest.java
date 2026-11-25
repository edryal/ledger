package org.codeberg.edryal.ledger.bookkeeping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.codeberg.edryal.ledger.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    Ledger ledger;

    @Test
    void newAccountStartsAtZero() {
        AccountId id = openWallet();

        assertThat(ledger.balanceOf(id)).isEqualTo(Money.zero(EUR));
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThatThrownBy(() -> ledger.balanceOf(AccountId.newId())).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void balanceHasTheAccountsCurrency() {
        AccountId id = ledger.open(new OpenAccount(AccountType.MERCHANT_BALANCE, USD, UUID.randomUUID()));

        assertThat(ledger.balanceOf(id)).isEqualTo(Money.zero(USD));
    }

    @Test
    void eachAccountGetsItsOwnId() {
        var command = new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID());

        assertThat(ledger.open(command)).isNotEqualTo(ledger.open(command));
    }

    @Test
    void depositIncreasesWalletAndCash() {
        AccountId wallet = openWallet();
        Money cashBefore = ledger.balanceOf(cash());

        PostResult result = deposit(wallet, 10000);

        assertThat(result).isInstanceOf(PostResult.Posted.class);
        assertThat(ledger.balanceOf(wallet)).isEqualTo(eur(10000));
        assertThat(ledger.balanceOf(cash())).isEqualTo(cashBefore.plus(eur(10000)));
    }

    @Test
    void transferMovesMoneyBetweenWallets() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 10000);

        PostResult result = transfer(catalin, bob, 2500);

        assertThat(result).isInstanceOf(PostResult.Posted.class);
        assertThat(ledger.balanceOf(catalin)).isEqualTo(eur(7500));
        assertThat(ledger.balanceOf(bob)).isEqualTo(eur(2500));
    }

    @Test
    void insufficientFundsIsRejectedAndNothingChanges() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 1500);

        PostResult result = transfer(catalin, bob, 6000);

        assertThat(result).isEqualTo(new PostResult.Rejected(RejectionReason.INSUFFICIENT_FUNDS, catalin));
        assertThat(ledger.balanceOf(catalin)).isEqualTo(eur(1500));
        assertThat(ledger.balanceOf(bob)).isEqualTo(eur(0));
    }

    @Test
    void sameReferenceCannotBePostedTwice() {
        AccountId wallet = openWallet();
        String reference = "deposit:" + UUID.randomUUID();
        var command = new PostEntry(reference, "deposit", List.of(
                Posting.debit(cash(), eur(100)), Posting.credit(wallet, eur(100))));

        ledger.post(command);

        assertThatThrownBy(() -> ledger.post(command)).isInstanceOf(DuplicateReferenceException.class);
        assertThat(ledger.balanceOf(wallet)).isEqualTo(eur(100));
    }

    @Test
    void unbalancedEntryIsRejected() {
        AccountId wallet = openWallet();

        assertThatThrownBy(() -> ledger.post(new PostEntry("test:" + UUID.randomUUID(), "unbalanced", List.of(
                Posting.debit(cash(), eur(100)), Posting.credit(wallet, eur(99))))))
                        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postingToUnknownAccountFails() {
        assertThatThrownBy(() -> ledger.post(new PostEntry("test:" + UUID.randomUUID(), "unknown", List.of(
                Posting.debit(cash(), eur(100)), Posting.credit(AccountId.newId(), eur(100))))))
                        .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void postingInWrongCurrencyFails() {
        AccountId wallet = openWallet();

        assertThatThrownBy(() -> ledger.post(new PostEntry("test:" + UUID.randomUUID(), "wrong currency", List.of(
                Posting.debit(ledger.systemAccount(SystemAccount.CASH, USD), Money.of(100, USD)),
                Posting.credit(wallet, Money.of(100, USD)))))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void concurrentTransfersNeverOverspendOrLoseMoney() throws Exception {
        int walletCount = 5;
        long startingBalance = 1000;
        List<AccountId> wallets = new ArrayList<>();

        for (int i = 0; i < walletCount; i++) {
            AccountId wallet = openWallet();
            deposit(wallet, startingBalance);
            wallets.add(wallet);
        }

        List<Callable<PostResult>> transfers = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            transfers.add(() -> {
                var random = ThreadLocalRandom.current();
                int from = random.nextInt(walletCount);
                int to = (from + 1 + random.nextInt(walletCount - 1)) % walletCount;
                return transfer(wallets.get(from), wallets.get(to), 1 + random.nextInt(400));
            });
        }

        List<PostResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<PostResult> future : pool.invokeAll(transfers)) {
                // rethrows any exception, e.g. a deadlock
                results.add(future.get());
            }
        }

        long total = 0;
        for (AccountId wallet : wallets) {
            Money balance = ledger.balanceOf(wallet);
            assertThat(balance.isNegative()).isFalse();
            total += balance.minorUnits();
        }

        assertThat(total).isEqualTo(walletCount * startingBalance);
        assertThat(results).hasOnlyElementsOfTypes(PostResult.Posted.class, PostResult.Rejected.class);
        assertThat(results).hasAtLeastOneElementOfType(PostResult.Posted.class);
    }

    @Test
    void openingAccountInUnsupportedCurrencyFails() {
        Currency chf = Currency.getInstance("CHF");

        assertThatThrownBy(() -> ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, chf, UUID.randomUUID())))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void systemAccountInUnsupportedCurrencyFails() {
        assertThatThrownBy(() -> ledger.systemAccount(SystemAccount.CASH, Currency.getInstance("CHF")))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    private AccountId openWallet() {
        return ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID()));
    }

    private AccountId cash() {
        return ledger.systemAccount(SystemAccount.CASH, EUR);
    }

    private PostResult deposit(AccountId wallet, long cents) {
        return ledger.post(new PostEntry("deposit:" + UUID.randomUUID(), "deposit", List.of(
                Posting.debit(cash(), eur(cents)), Posting.credit(wallet, eur(cents)))));
    }

    private PostResult transfer(AccountId from, AccountId to, long cents) {
        return ledger.post(new PostEntry("transfer:" + UUID.randomUUID(), "transfer", List.of(
                Posting.debit(from, eur(cents)), Posting.credit(to, eur(cents)))));
    }

    private static Money eur(long cents) {
        return Money.of(cents, EUR);
    }
}
