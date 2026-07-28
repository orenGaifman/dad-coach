package com.dadcoach.api.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore();
    }

    @Test
    void compositeKey_combinesActorIdAndIdempotencyKey() {
        UUID actorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String key = store.compositeKey(actorId, "my-key-123");
        assertThat(key).isEqualTo("11111111-1111-1111-1111-111111111111:my-key-123");
    }

    @Test
    void get_returnsEmpty_whenKeyNotPresent() {
        Optional<IdempotencyStore.CachedResponse> result = store.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void put_andGet_returnsCachedResponse() {
        byte[] body = "{\"id\":\"123\"}".getBytes();
        store.put("key1", 201, body, "application/json");

        Optional<IdempotencyStore.CachedResponse> result = store.get("key1");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(201);
        assertThat(result.get().body()).isEqualTo(body);
        assertThat(result.get().contentType()).isEqualTo("application/json");
    }

    @Test
    void sameKeyDifferentActors_areDifferentEntries() {
        UUID actor1 = UUID.randomUUID();
        UUID actor2 = UUID.randomUUID();

        String key1 = store.compositeKey(actor1, "same-key");
        String key2 = store.compositeKey(actor2, "same-key");

        store.put(key1, 200, "response1".getBytes(), "text/plain");
        store.put(key2, 201, "response2".getBytes(), "text/plain");

        assertThat(store.get(key1).get().status()).isEqualTo(200);
        assertThat(store.get(key2).get().status()).isEqualTo(201);
    }

    @Test
    void tryReserve_returnsTrueForNewKey() {
        boolean reserved = store.tryReserve("new-key");
        assertThat(reserved).isTrue();
    }

    @Test
    void tryReserve_returnsFalseForExistingKey() {
        store.tryReserve("existing-key");
        boolean secondReserve = store.tryReserve("existing-key");
        assertThat(secondReserve).isFalse();
    }

    @Test
    void isProcessing_returnsTrueForReservedKey() {
        store.tryReserve("processing-key");
        assertThat(store.isProcessing("processing-key")).isTrue();
    }

    @Test
    void isProcessing_returnsFalseAfterPut() {
        store.tryReserve("completed-key");
        store.put("completed-key", 200, "done".getBytes(), "text/plain");
        assertThat(store.isProcessing("completed-key")).isFalse();
    }

    @Test
    void remove_deletesKey() {
        store.put("to-remove", 200, "data".getBytes(), "text/plain");
        store.remove("to-remove");
        assertThat(store.get("to-remove")).isEmpty();
    }

    @Test
    void size_tracksStoredKeys() {
        assertThat(store.size()).isZero();
        store.put("key1", 200, "a".getBytes(), "text/plain");
        store.put("key2", 201, "b".getBytes(), "text/plain");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void cleanupExpiredKeys_removesOnlyExpiredEntries() {
        // Put a valid entry
        store.put("valid-key", 200, "data".getBytes(), "text/plain");

        // The cleanup should not remove it (TTL is 24h in the future)
        store.cleanupExpiredKeys();
        assertThat(store.get("valid-key")).isPresent();
    }
}
