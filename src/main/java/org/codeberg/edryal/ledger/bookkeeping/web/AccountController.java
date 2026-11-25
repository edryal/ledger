package org.codeberg.edryal.ledger.bookkeeping.web;

import java.net.URI;
import java.util.Currency;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.bookkeeping.AccountType;
import org.codeberg.edryal.ledger.bookkeeping.Ledger;
import org.codeberg.edryal.ledger.bookkeeping.OpenAccount;
import org.codeberg.edryal.ledger.common.web.MoneyBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/v1/accounts")
class AccountController {

    private final Ledger ledger;

    AccountController(Ledger ledger) {
        this.ledger = ledger;
    }

    record OpenAccountRequest(@NotNull AccountType type, @NotNull Currency currency, @NotNull UUID ownerId) {
    }

    record AccountResponse(UUID id, MoneyBody balance) {
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountId id = ledger.open(new OpenAccount(request.type(), request.currency(), request.ownerId()));
        return ResponseEntity.created(URI.create("/v1/accounts/" + id)).body(toResponse(id));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return toResponse(new AccountId(id));
    }

    private AccountResponse toResponse(AccountId id) {
        return new AccountResponse(id.value(), MoneyBody.from(ledger.balanceOf(id)));
    }
}
