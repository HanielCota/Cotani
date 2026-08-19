package com.cotani.economy.currency;

import com.cotani.text.MiniMessages;
import com.cotani.text.Placeholders;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class EconomyFormatter implements AutoCloseable {
    private final EconomyCurrency currency;
    private final ThreadLocal<DecimalFormat> decimalFormat;

    public EconomyFormatter(EconomyCurrency currency, Locale locale) {
        this.currency = Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(locale, "locale");

        var symbols = DecimalFormatSymbols.getInstance(locale);
        var pattern = currency.decimalPlaces() == 0 ? "#,##0" : "#,##0." + "0".repeat(currency.decimalPlaces());

        this.decimalFormat = ThreadLocal.withInitial(() -> {
            var format = new DecimalFormat(pattern, symbols);
            format.setRoundingMode(RoundingMode.DOWN);
            format.setMinimumFractionDigits(currency.decimalPlaces());
            format.setMaximumFractionDigits(currency.decimalPlaces());

            return format;
        });
    }

    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");

        return currency.symbol() + decimalFormat.get().format(amount);
    }

    public Component formatComponent(BigDecimal amount) {
        var formatted = format(amount);

        if (formatted.indexOf('<') < 0) {
            return Component.text(formatted);
        }

        return MiniMessages.parse(formatted);
    }

    /**
     * Creates a TagResolver placeholder for templates (e.g. Placeholders.unparsed("balance", formatted)).
     *
     * @param placeholderName placeholder tag name
     * @param amount currency amount
     * @return tag resolver for MiniMessages
     */
    public TagResolver asPlaceholder(String placeholderName, BigDecimal amount) {
        Objects.requireNonNull(placeholderName, "placeholderName");

        return Placeholders.unparsed(placeholderName, format(amount));
    }

    public void remove() {
        decimalFormat.remove();
    }

    @Override
    public void close() {
        decimalFormat.remove();
    }
}
