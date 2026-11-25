package org.codeberg.edryal.ledger.bookkeeping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.UUID;
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
        AccountId id = ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID()));

        assertThat(ledger.balanceOf(id)).isEqualTo(Money.zero(EUR));
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThatThrownBy(() -> ledger.balanceOf(AccountId.newId()))
                .isInstanceOf(AccountNotFoundException.class);
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
}
