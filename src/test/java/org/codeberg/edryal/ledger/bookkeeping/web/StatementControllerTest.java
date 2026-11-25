package org.codeberg.edryal.ledger.bookkeeping.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.OpenAccount;
import org.codeberg.edryal.ledger.bookkeeping.PostEntry;
import org.codeberg.edryal.ledger.bookkeeping.PostResult;
import org.codeberg.edryal.ledger.bookkeeping.Posting;
import org.codeberg.edryal.ledger.bookkeeping.SystemAccount;
import org.codeberg.edryal.ledger.common.Money;
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
class StatementControllerTest {

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
    void pagesThroughStatementNewestFirst() {
        AccountId wallet = openWallet();
        for (int i = 1; i <= 5; i++) {
            deposit(wallet, i * 100L);
        }

        List<Integer> amounts = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String uri = "/v1/accounts/" + wallet + "/postings?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String json = client.get()
                    .uri(uri)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();
            amounts.addAll(JsonPath.read(json, "$.data[*].amount.value"));
            cursor = JsonPath.read(json, "$.next_cursor");
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(amounts).containsExactly(500, 400, 300, 200, 100);
    }

    @Test
    void statementLineShowsEntryDetails() {
        AccountId wallet = openWallet();
        deposit(wallet, 2599);

        client.get()
                .uri("/v1/accounts/{id}/postings", wallet)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(1)
                .jsonPath("$.data[0].direction")
                .isEqualTo("CREDIT")
                .jsonPath("$.data[0].amount.value")
                .isEqualTo(2599)
                .jsonPath("$.data[0].amount.currency")
                .isEqualTo("EUR")
                .jsonPath("$.data[0].description")
                .isEqualTo("deposit")
                .jsonPath("$.next_cursor")
                .doesNotExist();
    }

    @Test
    void emptyStatementForNewAccount() {
        client.get()
                .uri("/v1/accounts/{id}/postings", openWallet())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    void statementOfUnknownAccountIs404() {
        client.get()
                .uri("/v1/accounts/{id}/postings", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void invalidCursorIs400() {
        client.get()
                .uri("/v1/accounts/{id}/postings?cursor=garbage", openWallet())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/invalid-cursor");
    }

    @Test
    void limitOutOfRangeIs400() {
        client.get()
                .uri("/v1/accounts/{id}/postings?limit=0", openWallet())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void journalEntryShowsAllPostings() {
        AccountId wallet = openWallet();
        var posted = (PostResult.Posted) deposit(wallet, 10000);

        client.get()
                .uri("/v1/journal-entries/{id}", posted.entryId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(posted.entryId().toString())
                .jsonPath("$.postings.length()")
                .isEqualTo(2)
                .jsonPath("$.postings[0].direction")
                .isEqualTo("DEBIT")
                .jsonPath("$.postings[1].direction")
                .isEqualTo("CREDIT")
                .jsonPath("$.postings[1].account_id")
                .isEqualTo(wallet.toString())
                .jsonPath("$.posted_at")
                .isNotEmpty();
    }

    @Test
    void unknownJournalEntryIs404() {
        client.get()
                .uri("/v1/journal-entries/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/not-found");
    }

    private AccountId openWallet() {
        return ledger.open(new OpenAccount(AccountType.CUSTOMER_WALLET, EUR, UUID.randomUUID()));
    }

    private PostResult deposit(AccountId wallet, long cents) {
        AccountId cash = ledger.systemAccount(SystemAccount.CASH, EUR);
        return ledger.post(new PostEntry("deposit:" + UUID.randomUUID(), "deposit", List.of(
                Posting.debit(cash, Money.of(cents, EUR)), Posting.credit(wallet, Money.of(cents, EUR)))));
    }
}
