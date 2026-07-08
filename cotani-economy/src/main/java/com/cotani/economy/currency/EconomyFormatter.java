package com.cotani.economy.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class EconomyFormatter implements AutoCloseable {

    private final EconomyCurrency currency;
    private final DecimalFormat decimalFormat;

    public EconomyFormatter(EconomyCurrency currency, Locale locale) {
        this.currency = Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");

        var symbols = DecimalFormatSymbols.getInstance(locale);
        var pattern = currency.decimalPlaces() == 0 ? "#,##0" : "#,##0." + "0".repeat(currency.decimalPlaces());

        this.decimalFormat = new DecimalFormat(pattern, symbols);
        this.decimalFormat.setRoundingMode(RoundingMode.DOWN);
        this.decimalFormat.setMinimumFractionDigits(currency.decimalPlaces());
        this.decimalFormat.setMaximumFractionDigits(currency.decimalPlaces());
    }

    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        synchronized (decimalFormat) {
            return currency.symbol() + decimalFormat.format(amount);
        }
    }

    public void remove() {
        // no-op, kept for backward compatibility
    }

    @Override
    public void close() {
        // no-op, kept for backward compatibility
    }
}
