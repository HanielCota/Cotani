package com.cotani.economy;

import com.cotani.economy.api.EconomyModule;
import com.cotani.economy.internal.DefaultEconomyModule;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Public entry point for the economy module.
 *
 * <p>Consumers should use {@link #create(EconomyModule.Context)} to obtain a fully wired {@link EconomyModule}.
 */
@NullMarked
public final class CotaniEconomy {

    private CotaniEconomy() {}

    public static EconomyModule create(EconomyModule.Context context) {
        Objects.requireNonNull(context, "context");
        return DefaultEconomyModule.create(context);
    }
}
