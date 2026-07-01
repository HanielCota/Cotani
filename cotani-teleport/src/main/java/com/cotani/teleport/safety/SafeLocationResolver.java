package com.cotani.teleport.safety;

import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;

public interface SafeLocationResolver {
    CompletableFuture<Optional<Location>> resolve(Location target, SafeLocationOptions options);
}
