package com.cotani.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.cache.builder.DataCacheBuilder;
import com.cotani.cache.builder.PlayerDataCacheBuilder;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CotaniCacheTest {
    @Test
    void dataReturnsPreconfiguredBuilder() {
        DataCacheBuilder<String, Integer> builder = CotaniCache.data(String.class, Integer.class);

        assertNotNull(builder);
    }

    @Test
    void playersReturnsPreconfiguredBuilder() {
        PlayerDataCacheBuilder<String> builder = CotaniCache.players(String.class);

        assertNotNull(builder);
    }

    @Test
    void temporaryReturnsPreconfiguredBuilder() {
        DataCacheBuilder<String, Integer> builder =
                CotaniCache.temporary(String.class, Integer.class, Duration.ofMinutes(5));

        assertNotNull(builder);
    }

    @Test
    void dataRejectsNullKeyType() {
        assertThrows(NullPointerException.class, () -> CotaniCache.data(null, Integer.class));
    }

    @Test
    void dataRejectsNullValueType() {
        assertThrows(NullPointerException.class, () -> CotaniCache.data(String.class, null));
    }

    @Test
    void playersRejectsNullValueType() {
        assertThrows(NullPointerException.class, () -> CotaniCache.players(null));
    }

    @Test
    void temporaryRejectsNullKeyType() {
        assertThrows(
                NullPointerException.class, () -> CotaniCache.temporary(null, Integer.class, Duration.ofMinutes(5)));
    }

    @Test
    void temporaryRejectsNullValueType() {
        assertThrows(
                NullPointerException.class, () -> CotaniCache.temporary(String.class, null, Duration.ofMinutes(5)));
    }

    @Test
    void temporaryRejectsNullDuration() {
        assertThrows(NullPointerException.class, () -> CotaniCache.temporary(String.class, Integer.class, null));
    }

    @Test
    void constructorThrowsUnsupportedOperationException() throws Exception {
        var constructor = CotaniCache.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, thrown.getCause());
    }
}
