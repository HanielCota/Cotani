package com.cotani.economy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class EconomyFormatterTest {

    @Test
    void formatIncludesSymbolAndDecimalPlaces() {
        var currency = new EconomyCurrency(CurrencyId.of("coins"), "Coins", "$", 2);
        var formatter = new EconomyFormatter(currency, Locale.US);

        var formatted = formatter.format(new BigDecimal("1234.50"));

        assertEquals("$1,234.50", formatted);
    }

    @Test
    void formatUsesZeroDecimalPlacesWhenConfigured() {
        var currency = new EconomyCurrency(CurrencyId.of("gems"), "Gems", "&#x2666;", 0);
        var formatter = new EconomyFormatter(currency, Locale.US);

        var formatted = formatter.format(new BigDecimal("999"));

        assertEquals("&#x2666;999", formatted);
    }

    @Test
    void formatIsThreadSafe() throws InterruptedException {
        var currency = EconomyCurrency.coins();
        var formatter = new EconomyFormatter(currency, Locale.US);
        var executor = Executors.newFixedThreadPool(8);
        var latch = new CountDownLatch(100);

        try {
            for (int i = 0; i < 100; i++) {
                var amount = BigDecimal.valueOf(i);
                var _ = executor.submit(() -> {
                    formatter.format(amount);
                    latch.countDown();
                });
            }
            latch.await();
        } finally {
            executor.shutdown();
        }

        assertEquals(0, latch.getCount());
    }
}
