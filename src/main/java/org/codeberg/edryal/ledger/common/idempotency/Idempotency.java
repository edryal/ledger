package org.codeberg.edryal.ledger.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a request safe to retry. The first request with a key runs and its response is stored.
 * Later requests with the same key and the same body get the stored response, without running again.
 *
 * <p>The key and the business change commit in one transaction. If the request fails, both roll back,
 * so a retry runs again instead of replaying the failure.
 */
@Component
public class Idempotency {

    /** Request header that carries the key. */
    public static final String HEADER = "Idempotency-Key";

    /** Response header set on replayed responses. */
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    // Until authentication exists (M5), all requests share one client.
    private static final String ANONYMOUS_CLIENT = "anonymous";

    private final IdempotencyRepository keys;
    private final JsonMapper json;

    Idempotency(IdempotencyRepository keys, JsonMapper json) {
        this.keys = keys;
        this.json = json;
    }

    /**
     * Runs {@code action} once per key.
     *
     * @param key          the {@code Idempotency-Key} header value, may be null
     * @param operation    identifies the endpoint, e.g. {@code "POST /v1/transfers"}
     * @param request      the parsed request body, used to detect a key reused for a different request
     * @param responseType the response body type, needed to read a stored response back
     * @throws MissingIdempotencyKeyException if the key is missing, blank or longer than 255 characters
     * @throws IdempotencyKeyReusedException  if the key was used before with a different request
     */
    @Transactional
    public <T> ResponseEntity<T> execute(String key, String operation, Object request, Class<T> responseType, Supplier<ResponseEntity<T>> action) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new MissingIdempotencyKeyException();
        }
        byte[] requestHash = hash(operation, request);

        if (!keys.claim(ANONYMOUS_CLIENT, key, requestHash)) {
            StoredResponse stored = keys.find(ANONYMOUS_CLIENT, key).orElseThrow();
            if (!Arrays.equals(stored.requestHash(), requestHash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return replay(stored, responseType);
        }

        ResponseEntity<T> response = action.get();
        String location = response.getHeaders().getLocation() == null ? null :
                response.getHeaders().getLocation().toString();

        keys.complete(ANONYMOUS_CLIENT, key, response.getStatusCode().value(), location,
                json.writeValueAsString(response.getBody()));
        return response;
    }

    private <T> ResponseEntity<T> replay(StoredResponse stored, Class<T> responseType) {
        var builder = ResponseEntity.status(stored.status()).header(REPLAYED_HEADER, "true");
        stored.location().ifPresent(location -> builder.header(HttpHeaders.LOCATION, location));
        return builder.body(json.readValue(stored.body(), responseType));
    }

    private byte[] hash(String operation, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(json.writeValueAsBytes(request));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
