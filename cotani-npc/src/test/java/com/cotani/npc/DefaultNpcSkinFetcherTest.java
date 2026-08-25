package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.npc.internal.DefaultNpcSkinFetcher;
import org.junit.jupiter.api.Test;

class DefaultNpcSkinFetcherTest {

    @Test
    void shouldConstructFetcher() {
        var fetcher = new DefaultNpcSkinFetcher();
        assertNotNull(fetcher);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullInputs() {
        var fetcher = new DefaultNpcSkinFetcher();
        assertThrows(NullPointerException.class, () -> fetcher.fetchByUsernameAsync(null));
        assertThrows(NullPointerException.class, () -> fetcher.fetchByUuidAsync(null));
    }

    @Test
    void shouldRejectInvalidExternalIdentifiersBeforeNetworking() {
        var fetcher = new DefaultNpcSkinFetcher();

        assertTrue(fetcher.fetchByUsernameAsync("invalid username!")
                .toCompletableFuture()
                .join()
                .isEmpty());
        assertTrue(fetcher.fetchByUuidAsync("not-a-mojang-uuid")
                .toCompletableFuture()
                .join()
                .isEmpty());
    }
}
