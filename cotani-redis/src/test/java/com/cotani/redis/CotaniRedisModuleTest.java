package com.cotani.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class CotaniRedisModuleTest {

    @Test
    void shouldWrapCotaniRedisAndClose() {
        var redis = mock(CotaniRedis.class);
        var module = CotaniRedisModule.of(redis);

        assertNotNull(module.redis());
        assertEquals(redis, module.redis());

        module.close();
        verify(redis).close();
    }
}
