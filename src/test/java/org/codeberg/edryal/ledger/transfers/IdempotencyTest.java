package org.codeberg.edryal.ledger.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class IdempotencyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

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
    void retryWithSameKeyReplaysTheFirstResponse() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 10000);
        String key = UUID.randomUUID().toString();
        String body = transferBody(catalin, bob, 2500);

        String first = transfer(key, body).expectStatus()
                .isCreated()
                .expectHeader()
                .doesNotExist(Idempotency.REPLAYED_HEADER)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        String second = transfer(key, body).expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals(Idempotency.REPLAYED_HEADER, "true")
                .expectHeader()
                .exists("Location")
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(ledger.balanceOf(catalin)).isEqualTo(Money.of(7500, EUR));
        assertThat(ledger.balanceOf(bob)).isEqualTo(Money.of(2500, EUR));
    }

    @Test
    void sameKeyWithDifferentBodyIs422() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 10000);
        String key = UUID.randomUUID().toString();

        transfer(key, transferBody(catalin, bob, 2500)).expectStatus().isCreated();

        transfer(key, transferBody(catalin, bob, 9999)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/idempotency-key-reused");
        assertThat(ledger.balanceOf(catalin)).isEqualTo(Money.of(7500, EUR));
    }

    @Test
    void sameKeyOnAnotherEndpointIs422() {
        AccountId wallet = openWallet();
        String key = UUID.randomUUID().toString();
        String string = """
                {"account_id": "%s", "amount": {"value": 100, "currency": "EUR"}}
                """;

        post("/v1/deposits", key, string.formatted(wallet)).expectStatus().isCreated();

        post("/v1/withdrawals", key, string.formatted(wallet)).expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/idempotency-key-reused");
    }

    @Test
    void missingKeyIs400() {
        AccountId wallet = openWallet();
        String string = """
                {"account_id": "%s", "amount": {"value": 100, "currency": "EUR"}}
                """;

        client.post()
                .uri("/v1/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(string.formatted(wallet))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/missing-idempotency-key");

        assertThat(ledger.balanceOf(wallet)).isEqualTo(Money.of(0, EUR));
    }

    @Test
    void failedRequestIsNotStoredSoARetryRunsAgain() {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        String key = UUID.randomUUID().toString();
        String body = transferBody(catalin, bob, 2500);

        transfer(key, body).expectStatus().isEqualTo(422);

        deposit(catalin, 10000);

        transfer(key, body).expectStatus().isCreated().expectHeader().doesNotExist(Idempotency.REPLAYED_HEADER);
        assertThat(ledger.balanceOf(bob)).isEqualTo(Money.of(2500, EUR));
    }

    @Test
    void concurrentRetriesWithSameKeyMoveMoneyOnce() throws Exception {
        AccountId catalin = openWallet();
        AccountId bob = openWallet();
        deposit(catalin, 10000);
        String key = UUID.randomUUID().toString();
        String body = transferBody(catalin, bob, 2500);

        List<Callable<String>> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            attempts.add(() -> {
                return transfer(key, body).expectStatus()
                        .isCreated()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();
            });
        }

        List<String> transferIds = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<String> future : pool.invokeAll(attempts)) {
                transferIds.add(JsonPath.read(future.get(), "$.id"));
            }
        }

        assertThat(transferIds).hasSize(10).containsOnly(transferIds.getFirst());
        assertThat(ledger.balanceOf(catalin)).isEqualTo(Money.of(7500, EUR));
        assertThat(ledger.balanceOf(bob)).isEqualTo(Money.of(2500, EUR));
    }

    private RestTestClient.ResponseSpec transfer(String key, String body) {
        return post("/v1/transfers", key, body);
    }

    private RestTestClient.ResponseSpec post(String uri, String key, String body) {
        return client.post()
                .uri(uri)
                .header(Idempotency.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private static String transferBody(AccountId source, AccountId destination, long cents) {
        String string = """
                {"source_account_id": "%s", "destination_account_id": "%s",
                 "amount": {"value": %d, "currency": "EUR"}}
                """;
        return string.formatted(source, destination, cents);
    }

    private AccountId openWallet() {
        return ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID()));
    }

    private void deposit(AccountId account, long cents) {
        String string = """
                {"account_id": "%s", "amount": {"value": %d, "currency": "EUR"}}
                """;
        post("/v1/deposits", UUID.randomUUID().toString(), string.formatted(account, cents)).expectStatus().isCreated();
    }
}
