package com.cotani.economy.internal.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EconomyConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadDefaultSettingsWhenConfigFileIsMissing() {
        var configuration = EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class));

        try {
            var settings = configuration.settings();

            assertEquals(CurrencyId.of("coins"), settings.defaultCurrency().id());
            assertEquals(2, settings.defaultCurrency().decimalPlaces());
            assertEquals(0, settings.startingBalance().compareTo(BigDecimal.ZERO));
            assertEquals(0, settings.maximumBalance().compareTo(new BigDecimal("1000000000000.00")));
            assertEquals(0, settings.maximumOperationAmount().compareTo(new BigDecimal("100000000.00")));
            assertEquals(0, settings.minimumPayAmount().compareTo(BigDecimal.ONE));
            assertEquals(60, settings.topCacheSeconds());
        } finally {
            configuration.close();
        }
    }

    @Test
    void shouldLoadCustomValuesFromConfigFile() throws Exception {
        Files.writeString(tempDir.resolve("economy.yml"), """
                economy:
                  currency:
                    id: "diamonds"
                    name: "Diamonds"
                    symbol: "D"
                    decimal-places: 0
                  starting-balance: "100"
                  limits:
                    maximum-balance: "5000"
                    maximum-operation-amount: "250"
                    minimum-pay-amount: "1"
                  cache:
                    top-expire-after-seconds: 45
                """, StandardCharsets.UTF_8);

        var configuration = EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class));

        try {
            var settings = configuration.settings();

            assertEquals(CurrencyId.of("diamonds"), settings.defaultCurrency().id());
            assertEquals("Diamonds", settings.defaultCurrency().name());
            assertEquals("D", settings.defaultCurrency().symbol());
            assertEquals(0, settings.defaultCurrency().decimalPlaces());
            assertEquals(0, settings.startingBalance().compareTo(new BigDecimal("100")));
            assertEquals(0, settings.maximumBalance().compareTo(new BigDecimal("5000")));
            assertEquals(0, settings.maximumOperationAmount().compareTo(new BigDecimal("250")));
            assertEquals(0, settings.minimumPayAmount().compareTo(BigDecimal.ONE));
            assertEquals(45, settings.topCacheSeconds());
        } finally {
            configuration.close();
        }
    }

    @Test
    void shouldScaleConfiguredAmountsToCurrencyDecimalPlaces() throws Exception {
        Files.writeString(tempDir.resolve("economy.yml"), """
                economy:
                  starting-balance: "10.5"
                """, StandardCharsets.UTF_8);

        var configuration = EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class));

        try {
            assertEquals(0, configuration.settings().startingBalance().compareTo(new BigDecimal("10.50")));
            assertEquals(2, configuration.settings().startingBalance().scale());
        } finally {
            configuration.close();
        }
    }

    @Test
    void shouldFailLoadingWhenAmountHasMoreDecimalPlacesThanCurrency() throws Exception {
        Files.writeString(tempDir.resolve("economy.yml"), """
                economy:
                  limits:
                    maximum-balance: "100.001"
                """, StandardCharsets.UTF_8);

        assertThrows(
                ArithmeticException.class,
                () -> EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class)));
    }

    @Test
    void shouldRejectInvalidCurrencyIdFromConfig() throws Exception {
        Files.writeString(tempDir.resolve("economy.yml"), """
                economy:
                  currency:
                    id: "INVALID ID"
                """, StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class)));
    }

    @Test
    void shouldCloseWithoutError() {
        var configuration = EconomyConfiguration.load(newPlugin(), mock(PaperTaskScheduler.class));

        assertDoesNotThrow(configuration::close);
        assertDoesNotThrow(configuration::close);
    }

    private Plugin newPlugin() {
        var plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("economy-config-test"));
        return plugin;
    }
}
