package com.cotani.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyDefinition;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NullAway")
class EconomySettingsTest {
    private static final EconomyCurrency COINS = EconomyCurrency.coins();
    private static final EconomyCurrency GEMS = new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0);

    private static final BigDecimal SCALED_ZERO =
            BigDecimal.ZERO.setScale(COINS.decimalPlaces(), RoundingMode.UNNECESSARY);
    private static final BigDecimal SCALED_MAX =
            new BigDecimal("1000000000000").setScale(COINS.decimalPlaces(), RoundingMode.UNNECESSARY);
    private static final BigDecimal SCALED_MAX_OP =
            new BigDecimal("100000000").setScale(COINS.decimalPlaces(), RoundingMode.UNNECESSARY);
    private static final BigDecimal SCALED_MIN_PAY =
            BigDecimal.ONE.setScale(COINS.decimalPlaces(), RoundingMode.UNNECESSARY);

    @Test
    void shouldCreateDefaultSettingsForSingleCurrency() {
        var settings = EconomySettings.defaultSettings(COINS);

        assertEquals(COINS, settings.defaultCurrency());
        assertEquals(1, settings.currencies().size());
        assertEquals(COINS, settings.requireCurrency(COINS.id()));
        assertEquals(0, settings.startingBalance().compareTo(SCALED_ZERO));
        assertEquals(0, settings.maximumBalance().compareTo(SCALED_MAX));
        assertEquals(0, settings.maximumOperationAmount().compareTo(SCALED_MAX_OP));
        assertEquals(0, settings.minimumPayAmount().compareTo(SCALED_MIN_PAY));
        assertEquals(60, settings.topCacheSeconds());
    }

    @Test
    void shouldMergeDefaultAndExtraCurrencies() {
        var settings = EconomySettings.defaultSettings(COINS, List.of(GEMS));

        assertEquals(2, settings.currencies().size());
        assertEquals(COINS, settings.requireCurrency(COINS.id()));
        assertEquals(GEMS, settings.requireCurrency(GEMS.id()));
    }

    @Test
    void shouldNotOverrideDefaultCurrencyWithExtraCurrency() {
        var impostor = new EconomyCurrency(COINS.id(), "Impostor", "I", 0);
        var settings = EconomySettings.defaultSettings(COINS, List.of(impostor));

        assertEquals(COINS, settings.requireCurrency(COINS.id()));
        assertEquals(1, settings.currencies().size());
    }

    @Test
    void shouldSupportConvenienceConstructors() {
        var full = new EconomySettings(
                COINS, Map.of(COINS.id(), COINS), SCALED_ZERO, SCALED_MAX, SCALED_MAX_OP, SCALED_MIN_PAY, 45);
        var single = new EconomySettings(COINS, SCALED_ZERO, SCALED_MAX, SCALED_MAX_OP, SCALED_MIN_PAY, 45);

        assertEquals(45, single.topCacheSeconds());
        assertEquals(COINS, single.requireCurrency(COINS.id()));
        assertEquals(
                0, full.requireEnabledDefinition(COINS.id()).startingBalance().signum());
    }

    @Test
    void shouldRegisterDefaultDefinitionForCurrenciesWithoutCustomDefinition() {
        var settings = EconomySettings.defaultSettings(COINS, List.of(GEMS));

        var definition = settings.requireEnabledDefinition(GEMS.id());

        assertTrue(definition.enabled());
        assertEquals(0, definition.startingBalance().signum());
        assertEquals(0, settings.startingBalance(GEMS.id()).signum());
        assertEquals(0, settings.maximumBalance(GEMS.id()).compareTo(SCALED_MAX));
        assertEquals(0, settings.maximumOperationAmount(GEMS.id()).compareTo(SCALED_MAX_OP));
        assertEquals(0, settings.minimumPayAmount(GEMS.id()).compareTo(SCALED_MIN_PAY));
    }

    @Test
    void shouldUseCustomDefinitionLimitsPerCurrency() {
        var customDefinition = new CurrencyDefinition(
                GEMS, BigDecimal.TEN, new BigDecimal("1000"), new BigDecimal("100"), BigDecimal.ONE, true);
        var settings = new EconomySettings(
                COINS,
                Map.of(COINS.id(), COINS, GEMS.id(), GEMS),
                Map.of(GEMS.id(), customDefinition),
                SCALED_ZERO,
                SCALED_MAX,
                SCALED_MAX_OP,
                SCALED_MIN_PAY,
                60);

        assertEquals(0, settings.startingBalance(GEMS.id()).compareTo(BigDecimal.TEN));
        assertEquals(0, settings.maximumBalance(GEMS.id()).compareTo(new BigDecimal("1000")));
        assertEquals(0, settings.maximumOperationAmount(GEMS.id()).compareTo(new BigDecimal("100")));
        assertEquals(0, settings.minimumPayAmount(GEMS.id()).compareTo(BigDecimal.ONE));
        assertEquals(0, settings.decimalPlaces(GEMS.id()));
        assertEquals(COINS.decimalPlaces(), settings.decimalPlaces(COINS.id()));
    }

    @Test
    void shouldFindCurrencyWhenPresentAndAbsent() {
        var settings = EconomySettings.defaultSettings(COINS);

        assertTrue(settings.findCurrency(COINS.id()).isPresent());
        assertTrue(settings.findCurrency(CurrencyId.of("missing")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> settings.requireCurrency(CurrencyId.of("missing")));
    }

    @Test
    void shouldRejectDefinitionWithoutRegisteredCurrency() {
        var definition =
                new CurrencyDefinition(GEMS, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true);

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> new EconomySettings(
                        COINS,
                        Map.of(COINS.id(), COINS),
                        Map.of(GEMS.id(), definition),
                        SCALED_ZERO,
                        SCALED_MAX,
                        SCALED_MAX_OP,
                        SCALED_MIN_PAY,
                        60));

        assertTrue(failure.getMessage().contains("no registered currency"));
    }

    @Test
    void shouldRejectDefinitionThatDoesNotMatchRegisteredCurrency() {
        var mismatch = new EconomyCurrency(CurrencyId.of("other"), "Other", "O", 0);
        var definition = new CurrencyDefinition(
                mismatch, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true);

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> new EconomySettings(
                        COINS,
                        Map.of(COINS.id(), COINS),
                        Map.of(COINS.id(), definition),
                        SCALED_ZERO,
                        SCALED_MAX,
                        SCALED_MAX_OP,
                        SCALED_MIN_PAY,
                        60));

        assertTrue(failure.getMessage().contains("does not match registered currency"));
    }

    @Test
    void shouldRejectDefinitionValuesNotRepresentableByCurrencyScale() {
        var definition = new CurrencyDefinition(
                GEMS, new BigDecimal("1.50"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true);

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> new EconomySettings(
                        COINS,
                        Map.of(COINS.id(), COINS, GEMS.id(), GEMS),
                        Map.of(GEMS.id(), definition),
                        SCALED_ZERO,
                        SCALED_MAX,
                        SCALED_MAX_OP,
                        SCALED_MIN_PAY,
                        60));

        assertTrue(failure.getMessage().contains("not representable by currency"));
    }

    @Test
    void shouldRejectDisabledDefaultCurrency() {
        var disabledDefinition =
                new CurrencyDefinition(COINS, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, false);

        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> new EconomySettings(
                        COINS,
                        Map.of(COINS.id(), COINS),
                        Map.of(COINS.id(), disabledDefinition),
                        SCALED_ZERO,
                        SCALED_MAX,
                        SCALED_MAX_OP,
                        SCALED_MIN_PAY,
                        60));

        assertTrue(failure.getMessage().contains("default currency must be enabled"));
    }

    @Test
    void shouldRejectDisabledCurrencyOnRequireEnabledDefinition() {
        var disabledDefinition =
                new CurrencyDefinition(GEMS, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, false);
        var settings = new EconomySettings(
                COINS,
                Map.of(COINS.id(), COINS, GEMS.id(), GEMS),
                Map.of(GEMS.id(), disabledDefinition),
                SCALED_ZERO,
                SCALED_MAX,
                SCALED_MAX_OP,
                SCALED_MIN_PAY,
                60);

        var failure = assertThrows(IllegalArgumentException.class, () -> settings.requireEnabledDefinition(GEMS.id()));

        assertTrue(failure.getMessage().contains("Currency is disabled"));
    }

    @Test
    void shouldRejectDisabledCurrencyOnLimitAccessors() {
        var disabledDefinition =
                new CurrencyDefinition(GEMS, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, false);
        var settings = new EconomySettings(
                COINS,
                Map.of(COINS.id(), COINS, GEMS.id(), GEMS),
                Map.of(GEMS.id(), disabledDefinition),
                SCALED_ZERO,
                SCALED_MAX,
                SCALED_MAX_OP,
                SCALED_MIN_PAY,
                60);

        assertThrows(IllegalArgumentException.class, () -> settings.startingBalance(GEMS.id()));
        assertThrows(IllegalArgumentException.class, () -> settings.decimalPlaces(GEMS.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-0.01"})
    void shouldRejectNegativeStartingBalance(String rawAmount) {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> settingsWith(new BigDecimal(rawAmount), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO));

        assertTrue(failure.getMessage().contains("startingBalance cannot be negative"));
    }

    private static EconomySettings settingsWith(
            BigDecimal startingBalance,
            BigDecimal maximumBalance,
            BigDecimal maximumOperationAmount,
            BigDecimal minimumPayAmount) {
        return new EconomySettings(
                COINS,
                Map.of(COINS.id(), COINS),
                startingBalance,
                maximumBalance,
                maximumOperationAmount,
                minimumPayAmount,
                60);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void shouldRejectNonPositiveMaximumBalance(String rawAmount) {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> settingsWith(BigDecimal.ZERO, new BigDecimal(rawAmount), BigDecimal.ONE, BigDecimal.ZERO));

        assertTrue(failure.getMessage().contains("maximumBalance must be positive"));
    }

    @Test
    void shouldRejectStartingBalanceGreaterThanMaximumBalance() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> settingsWith(new BigDecimal("11"), new BigDecimal("10"), BigDecimal.ONE, BigDecimal.ZERO));

        assertTrue(failure.getMessage().contains("startingBalance cannot be greater than maximumBalance"));
    }

    @Test
    void shouldRejectMaximumOperationAmountGreaterThanMaximumBalance() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> settingsWith(BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("11"), BigDecimal.ZERO));

        assertTrue(failure.getMessage().contains("maximumOperationAmount cannot be greater than maximumBalance"));
    }

    @Test
    void shouldRejectMinimumPayAmountGreaterThanMaximumOperationAmount() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> settingsWith(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("11")));

        assertTrue(failure.getMessage().contains("minimumPayAmount cannot be greater than maximumOperationAmount"));
    }

    @Test
    void shouldRejectNegativeTopCacheDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomySettings(
                        COINS,
                        Map.of(COINS.id(), COINS),
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        -1));
    }

    @Test
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class, () -> EconomySettings.defaultSettings(null));
        assertThrows(NullPointerException.class, () -> EconomySettings.defaultSettings(COINS, null));
        assertThrows(
                NullPointerException.class,
                () -> new EconomySettings(
                        null,
                        Map.of(COINS.id(), COINS),
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        60));
        assertThrows(
                NullPointerException.class,
                () -> new EconomySettings(
                        COINS, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, 60));
        assertThrows(
                NullPointerException.class, () -> settingsWith(null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(
                NullPointerException.class, () -> settingsWith(BigDecimal.ZERO, null, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(
                NullPointerException.class, () -> settingsWith(BigDecimal.ZERO, BigDecimal.TEN, null, BigDecimal.ZERO));
        assertThrows(
                NullPointerException.class, () -> settingsWith(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, null));
        assertThrows(
                NullPointerException.class,
                () -> new EconomySettings(
                                COINS,
                                Map.of(COINS.id(), COINS),
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                BigDecimal.TEN,
                                BigDecimal.ZERO,
                                60)
                        .findCurrency(null));
        assertThrows(
                NullPointerException.class,
                () -> EconomySettings.defaultSettings(COINS).requireCurrency(null));
    }

    @Test
    void shouldEqualSettingsWithSameValues() {
        var settings = EconomySettings.defaultSettings(COINS);

        assertEquals(settings, EconomySettings.defaultSettings(COINS));
        assertNotEquals(settings, EconomySettings.defaultSettings(COINS, List.of(GEMS)));
        assertNotEquals(settings, null);
        assertNotEquals(settings, "settings");
    }

    @Test
    void shouldExposeImmutableCurrencyMaps() {
        var settings = EconomySettings.defaultSettings(COINS, List.of(GEMS));
        var extraId = CurrencyId.of("extra");

        assertThrows(
                UnsupportedOperationException.class, () -> settings.currencies().put(extraId, COINS));
        assertThrows(
                UnsupportedOperationException.class,
                () -> settings.currencyDefinitions()
                        .put(
                                extraId,
                                new CurrencyDefinition(
                                        COINS,
                                        BigDecimal.ZERO,
                                        BigDecimal.TEN,
                                        BigDecimal.TEN,
                                        BigDecimal.ZERO,
                                        true)));
    }
}
