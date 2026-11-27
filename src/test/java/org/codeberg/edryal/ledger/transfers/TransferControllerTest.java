package org.codeberg.edryal.ledger.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.UUID;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.OpenAccount;
import org.codeberg.edryal.ledger.common.Money;
import org.codeberg.edryal.ledger.common.idempotency.Idempotency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferControllerTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    WebApplicationContext context;

    @Autowired
    Ledger ledger;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void depositCreditsTheAccount() {
        AccountId wallet = openWallet(EUR);

        post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": 10000, "currency": "EUR"}, "description": "salary"}
                """.formatted(wallet)).expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("DEPOSIT")
                .jsonPath("$.source_account_id")
                .doesNotExist()
                .jsonPath("$.destination_account_id")
                .isEqualTo(wallet.toString())
                .jsonPath("$.amount.value")
                .isEqualTo(10000)
                .jsonPath("$.journal_entry_id")
                .isNotEmpty();

        assertThat(ledger.balanceOf(wallet)).isEqualTo(Money.of(10000, EUR));
    }

    @Test
    void depositIntoUnknownAccountIs404() {
        post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": 100, "currency": "EUR"}}
                """.formatted(UUID.randomUUID())).expectStatus().isNotFound();
    }

    @Test
    void depositInWrongCurrencyIs422() {
        AccountId wallet = openWallet(EUR);

        post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": 100, "currency": "USD"}}
                """.formatted(wallet)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/currency-mismatch");
    }

    @Test
    void depositInUnsupportedCurrencyIs422() {
        AccountId wallet = openWallet(EUR);

        post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": 100, "currency": "CHF"}}
                """.formatted(wallet)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/unsupported-currency");
    }

    @Test
    void zeroAmountIs400() {
        AccountId wallet = openWallet(EUR);

        post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": 0, "currency": "EUR"}}
                """.formatted(wallet)).expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void withdrawalDebitsTheAccount() {
        AccountId wallet = openWallet(EUR);
        deposit(wallet, 10000);

        post("/v1/withdrawals", """
                {"account_id": "%s", "amount": {"value": 3000, "currency": "EUR"}}
                """.formatted(wallet)).expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("WITHDRAWAL")
                .jsonPath("$.destination_account_id")
                .doesNotExist();

        assertThat(ledger.balanceOf(wallet)).isEqualTo(Money.of(7000, EUR));
    }

    @Test
    void withdrawingMoreThanAvailableIs422() {
        AccountId wallet = openWallet(EUR);
        deposit(wallet, 1000);

        post("/v1/withdrawals", """
                {"account_id": "%s", "amount": {"value": 5000, "currency": "EUR"}}
                """.formatted(wallet)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/insufficient-funds")
                .jsonPath("$.detail")
                .isEqualTo(
                        "account %s has 10.00 EUR available, 50.00 EUR required".formatted(wallet));

        assertThat(ledger.balanceOf(wallet)).isEqualTo(Money.of(1000, EUR));
    }

    @Test
    void transferMovesMoneyAndCanBeReadBack() {
        AccountId catalin = openWallet(EUR);
        AccountId bob = openWallet(EUR);
        deposit(catalin, 10000);

        String location = post("/v1/transfers", """
                {"source_account_id": "%s", "destination_account_id": "%s",
                 "amount": {"value": 2500, "currency": "EUR"}, "description": "Dinner"}
                """.formatted(catalin, bob)).expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("TRANSFER")
                .jsonPath("$.description")
                .isEqualTo("Dinner")
                .returnResult()
                .getResponseHeaders()
                .getLocation()
                .toString();

        assertThat(ledger.balanceOf(catalin)).isEqualTo(Money.of(7500, EUR));
        assertThat(ledger.balanceOf(bob)).isEqualTo(Money.of(2500, EUR));

        client.get()
                .uri(location)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.source_account_id")
                .isEqualTo(catalin.toString())
                .jsonPath("$.destination_account_id")
                .isEqualTo(bob.toString())
                .jsonPath("$.amount.value")
                .isEqualTo(2500);
    }

    @Test
    void transferWithInsufficientFundsIs422AndChangesNothing() {
        AccountId catalin = openWallet(EUR);
        AccountId bob = openWallet(EUR);
        deposit(catalin, 1500);

        post("/v1/transfers", """
                {"source_account_id": "%s", "destination_account_id": "%s",
                 "amount": {"value": 6000, "currency": "EUR"}}
                """.formatted(catalin, bob)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/insufficient-funds");

        assertThat(ledger.balanceOf(catalin)).isEqualTo(Money.of(1500, EUR));
        assertThat(ledger.balanceOf(bob)).isEqualTo(Money.of(0, EUR));
    }

    @Test
    void transferBetweenCurrenciesIs422() {
        AccountId euros = openWallet(EUR);
        AccountId dollars = openWallet(USD);
        deposit(euros, 1000);

        post("/v1/transfers", """
                {"source_account_id": "%s", "destination_account_id": "%s",
                 "amount": {"value": 500, "currency": "EUR"}}
                """.formatted(euros, dollars)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/currency-mismatch");
    }

    @Test
    void transferToSameAccountIs422() {
        AccountId wallet = openWallet(EUR);

        post("/v1/transfers", """
                {"source_account_id": "%s", "destination_account_id": "%s",
                 "amount": {"value": 100, "currency": "EUR"}}
                """.formatted(wallet, wallet)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/same-account");
    }

    @Test
    void unknownTransferIs404() {
        client.get()
                .uri("/v1/transfers/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/not-found");
    }

    private RestTestClient.ResponseSpec post(String uri, String json) {
        return client.post()
                .uri(uri)
                .header(Idempotency.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private AccountId openWallet(Currency currency) {
        return ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, currency, UUID.randomUUID()));
    }

    private void deposit(AccountId account, long cents) {
        String json = post("/v1/deposits", """
                {"account_id": "%s", "amount": {"value": %d, "currency": "%s"}}
                """.formatted(account, cents, ledger.balanceOf(account).currency().getCurrencyCode())).expectStatus()
                .isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat((String) JsonPath.read(json, "$.kind")).isEqualTo("DEPOSIT");
    }
}
