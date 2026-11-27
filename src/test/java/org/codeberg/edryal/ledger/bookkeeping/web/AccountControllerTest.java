package org.codeberg.edryal.ledger.bookkeeping.web;

import java.util.UUID;

import org.codeberg.edryal.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountControllerTest {

    @Autowired
    WebApplicationContext context;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void opensAccountAndReadsItBack() {
        String string = """
                {"type": "CUSTOMER_WALLET", "currency": "EUR", "owner_id": "%s"}
                """;

        String location = client.post()
                .uri("/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(string.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.balance.value")
                .isEqualTo(0)
                .jsonPath("$.balance.currency")
                .isEqualTo("EUR")
                .returnResult()
                .getResponseHeaders()
                .getLocation()
                .toString();

        client.get()
                .uri(location)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.balance.value")
                .isEqualTo(0);
    }

    @Test
    void unknownAccountIsProblem404() {
        client.get()
                .uri("/v1/accounts/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/not-found")
                .jsonPath("$.status")
                .isEqualTo(404);
    }

    @Test
    void malformedIdIsProblem400() {
        client.get()
                .uri("/v1/accounts/not-a-uuid")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void missingFieldIsProblem400() {
        String body = """
                {"type": "CUSTOMER_WALLET", "currency": "EUR"}
                """;

        client.post()
                .uri("/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
