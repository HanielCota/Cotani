package com.cotani.teleport.safety;

import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.bukkit.Location;

public interface SafeLocationResolver {
    CompletionStage<Optional<Location>> resolve(Location target, SafeLocationOptions options);
}
