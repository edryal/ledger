package org.codeberg.edryal.ledger.bookkeeping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.codeberg.edryal.ledger.bookkeeping.application.LedgerHealthIndicator;
import org.codeberg.edryal.ledger.bookkeeping.application.LedgerIntegrity;
import org.codeberg.edryal.ledger.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerIntegrityTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    Ledger ledger;

    @Autowired
    LedgerIntegrity integrity;

    @Autowired
    LedgerHealthIndicator health;

    @Autowired
    JdbcClient jdbc;

    @Test
    void consistentLedgerHasNoProblems() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 10000);
        transfer(catalin, bob, 2500);

        assertThat(integrity.check()).isEmpty();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void detectsStoredBalanceThatDoesNotMatchPostings() {
        AccountId wallet = openWallet();
        deposit(wallet, 10000);

        // Simulate a bug that changed a balance without a posting.
        setBalance(wallet, 99999);
        try {
            assertThat(integrity.check()).singleElement()
                    .asString()
                    .contains(wallet.toString())
                    .contains("99999")
                    .contains("10000");
            assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        } finally {
            // Other tests share this database, so put the correct balance back.
            setBalance(wallet, 10000);
        }

        assertThat(integrity.check()).isEmpty();
    }

    private void setBalance(AccountId account, long balance) {
        jdbc.sql("UPDATE account_balances SET balance = :balance WHERE account_id = :id")
                .param("balance", balance)
                .param("id", account.value())
                .update();
    }

    private AccountId openWallet() {
        return ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID()));
    }

    private void deposit(AccountId wallet, long cents) {
        AccountId cash = ledger.systemAccount(SystemAccount.CASH, EUR);
        var command = new PostEntry(
                "deposit:" + UUID.randomUUID(),
                "deposit",
                List.of(Posting.debit(cash, Money.of(cents, EUR)), Posting.credit(wallet, Money.of(cents, EUR))));

        ledger.post(command);
    }

    private void transfer(AccountId from, AccountId to, long cents) {
        var command = new PostEntry(
                "transfer:" + UUID.randomUUID(),
                "transfer",
                List.of(Posting.debit(from, Money.of(cents, EUR)), Posting.credit(to, Money.of(cents, EUR))));

        ledger.post(command);
    }
}
