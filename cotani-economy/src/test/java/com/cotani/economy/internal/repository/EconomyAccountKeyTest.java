package com.cotani.economy.internal.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.economy.currency.CurrencyId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyAccountKeyTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrencyId COINS = CurrencyId.of("coins");
    private static final CurrencyId GEMS = CurrencyId.of("gems");

    @Test
    void shouldBeEqualForSameUserAndCurrency() {
        assertEquals(new EconomyAccountKey(USER_ID, COINS), new EconomyAccountKey(USER_ID, COINS));
    }

    @Test
    void shouldDifferWhenUserOrCurrencyChanges() {
        var key = new EconomyAccountKey(USER_ID, COINS);

        assertNotEquals(key, new EconomyAccountKey(UUID.randomUUID(), COINS));
        assertNotEquals(key, new EconomyAccountKey(USER_ID, GEMS));
        assertNotEquals(key, null);
        assertNotEquals(key, "key");
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullComponents() {
        assertThrows(NullPointerException.class, () -> new EconomyAccountKey(null, COINS));
        assertThrows(NullPointerException.class, () -> new EconomyAccountKey(USER_ID, null));
    }

    @Test
    void shouldOrderByUserIdThenCurrencyId() {
        var smallerUser = new EconomyAccountKey(UUID.randomUUID(), COINS);
        var otherUser = new EconomyAccountKey(UUID.randomUUID(), COINS);
        var key = new EconomyAccountKey(smallerUser.userId(), GEMS);

        if (smallerUser.userId().compareTo(otherUser.userId()) < 0) {
            assertTrue(key.compareTo(new EconomyAccountKey(otherUser.userId(), COINS)) < 0);
            assertTrue(new EconomyAccountKey(otherUser.userId(), COINS).compareTo(key) > 0);
        }
        if (smallerUser.userId().compareTo(otherUser.userId()) > 0) {
            assertTrue(key.compareTo(new EconomyAccountKey(otherUser.userId(), COINS)) > 0);
            assertTrue(new EconomyAccountKey(otherUser.userId(), COINS).compareTo(key) < 0);
        }

        assertTrue(key.compareTo(new EconomyAccountKey(smallerUser.userId(), CurrencyId.of("aaa"))) > 0);
        assertTrue(key.compareTo(new EconomyAccountKey(smallerUser.userId(), CurrencyId.of("zzz"))) < 0);
        assertEquals(0, key.compareTo(new EconomyAccountKey(smallerUser.userId(), GEMS)));
    }

    @Test
    void shouldBeUsableAsHashMapKey() {
        var key = new EconomyAccountKey(USER_ID, COINS);

        var map = new java.util.HashMap<EconomyAccountKey, String>();
        map.put(key, "value");

        assertEquals("value", map.get(new EconomyAccountKey(USER_ID, COINS)));
        assertNull(map.get(new EconomyAccountKey(USER_ID, GEMS)));
    }
}
