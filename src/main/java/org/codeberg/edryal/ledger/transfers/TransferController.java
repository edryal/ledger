package org.codeberg.edryal.ledger.transfers;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.codeberg.edryal.ledger.bookkeeping.AccountId;
import org.codeberg.edryal.ledger.common.idempotency.Idempotency;
import org.codeberg.edryal.ledger.common.web.AmountBody;
import org.codeberg.edryal.ledger.common.web.MoneyBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
class TransferController {

    private final TransferService service;
    private final Idempotency idempotency;

    TransferController(TransferService service, Idempotency idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    record DepositRequest(@NotNull UUID accountId, @Valid @NotNull AmountBody amount,
                          @Size(max = 255) String description) {
    }

    record WithdrawalRequest(@NotNull UUID accountId, @Valid @NotNull AmountBody amount,
                             @Size(max = 255) String description) {
    }

    record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                           @Valid @NotNull AmountBody amount, @Size(max = 255) String description) {
    }

    record TransferResponse(UUID id, TransferKind kind, UUID sourceAccountId, UUID destinationAccountId,
                            MoneyBody amount, String description, UUID journalEntryId, Instant createdAt) {
    }

    @PostMapping("/v1/deposits")
    ResponseEntity<TransferResponse> deposit(@RequestHeader(name = Idempotency.HEADER, required = false) String key, @Valid @RequestBody DepositRequest request) {
        return idempotency.execute(key, "POST /v1/deposits", request, TransferResponse.class, () -> created(service
                .deposit(new AccountId(request.accountId()), request.amount().toMoney(), request.description())));
    }

    @PostMapping("/v1/withdrawals")
    ResponseEntity<TransferResponse> withdraw(@RequestHeader(name = Idempotency.HEADER, required = false) String key, @Valid @RequestBody WithdrawalRequest request) {
        return idempotency.execute(key, "POST /v1/withdrawals", request, TransferResponse.class, () -> created(service
                .withdraw(new AccountId(request.accountId()), request.amount().toMoney(), request.description())));
    }

    @PostMapping("/v1/transfers")
    ResponseEntity<TransferResponse> transfer(@RequestHeader(name = Idempotency.HEADER, required = false) String key, @Valid @RequestBody TransferRequest request) {
        return idempotency.execute(key, "POST /v1/transfers", request, TransferResponse.class,
                () -> created(service.transfer(new AccountId(request.sourceAccountId()),
                        new AccountId(request.destinationAccountId()), request.amount().toMoney(),
                        request.description())));
    }

    @GetMapping("/v1/transfers/{id}")
    TransferResponse get(@PathVariable UUID id) {
        return toResponse(service.get(new TransferId(id)));
    }

    private static ResponseEntity<TransferResponse> created(Transfer transfer) {
        return ResponseEntity.created(URI.create("/v1/transfers/" + transfer.id())).body(toResponse(transfer));
    }

    private static TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.id().value(), transfer.kind(), transfer.source().map(AccountId::value).orElse(null),
                transfer.destination().map(AccountId::value).orElse(null), MoneyBody.from(transfer.amount()),
                transfer.description(), transfer.journalEntryId().value(), transfer.createdAt());
    }
}
