package com.cotani.economy;

import com.cotani.economy.api.EconomyModule;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Public entrypoint factory for the {@code cotani-economy} module.
 */
@NullMarked
public final class CotaniEconomies {

    private CotaniEconomies() {}

    /**
     * Creates and initializes a fully wired {@link EconomyModule}.
     *
     * @param context configuration context
     * @return initialized economy module
     */
    public static EconomyModule create(EconomyModule.Context context) {
        Objects.requireNonNull(context, "context");
        return CotaniEconomy.create(context);
    }
}
