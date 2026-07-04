package com.cotani.economy.internal.config;

import com.cotani.config.CotaniConfig;
import com.cotani.config.CotaniConfigs;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class EconomyConfiguration implements AutoCloseable {

    private final CotaniConfigs configs;
    private final EconomySettings settings;

    private EconomyConfiguration(CotaniConfigs configs, EconomySettings settings) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public static EconomyConfiguration load(Plugin plugin, PaperTaskScheduler scheduler) {
        CotaniConfigs configs = CotaniConfigs.create(plugin)
                .scheduler(scheduler)
                .file("economy.yml")
                .load();

        return new EconomyConfiguration(configs, loadSettings(configs.file("economy.yml")));
    }

    private static EconomySettings loadSettings(CotaniConfig config) {
        EconomyCurrency currency = loadCurrency(config);
        int decimalPlaces = currency.decimalPlaces();

        return new EconomySettings(
                currency,
                getBigDecimal(config, "economy.starting-balance", BigDecimal.ZERO, decimalPlaces),
                getBigDecimal(config, "economy.limits.maximum-balance", new BigDecimal("1000000000000"), decimalPlaces),
                getBigDecimal(
                        config, "economy.limits.maximum-operation-amount", new BigDecimal("100000000"), decimalPlaces),
                getBigDecimal(config, "economy.limits.minimum-pay-amount", BigDecimal.ONE, decimalPlaces),
                config.getInt("economy.cache.balance-expire-after-seconds", 30),
                config.getInt("economy.cache.top-expire-after-seconds", 60));
    }

    private static EconomyCurrency loadCurrency(CotaniConfig config) {
        return new EconomyCurrency(
                CurrencyId.of(config.getString("economy.currency.id", "coins")),
                config.getString("economy.currency.name", "Coins"),
                config.getString("economy.currency.symbol", "$"),
                config.getInt("economy.currency.decimal-places", 2));
    }

    private static BigDecimal getBigDecimal(CotaniConfig config, String path, BigDecimal fallback, int scale) {
        if (!config.contains(path)) {
            return fallback.setScale(scale, RoundingMode.UNNECESSARY);
        }
        return new BigDecimal(config.getString(path, fallback.toPlainString()))
                .setScale(scale, RoundingMode.UNNECESSARY);
    }

    public EconomySettings settings() {
        return settings;
    }

    @Override
    public void close() {
        configs.close();
    }
}
