package com.cotani.location.api;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Complete immutable state exchanged with a location repository. */
public record LocationSnapshot(List<Home> homes, List<Warp> warps) {
    public LocationSnapshot {
        homes = List.copyOf(Objects.requireNonNull(homes, "homes"));
        warps = List.copyOf(Objects.requireNonNull(warps, "warps"));
        homes.forEach(home -> Objects.requireNonNull(home, "home"));
        warps.forEach(warp -> Objects.requireNonNull(warp, "warp"));
        if (new HashSet<>(homes.stream().map(Home::id).toList()).size() != homes.size()) {
            throw new IllegalArgumentException("duplicate home id");
        }
        if (new HashSet<>(warps.stream().map(Warp::id).toList()).size() != warps.size()) {
            throw new IllegalArgumentException("duplicate warp id");
        }
        homes = homes.stream()
                .sorted(Comparator.comparing((Home home) -> home.id().ownerId().toString())
                        .thenComparing(home -> home.id().name().value()))
                .toList();
        warps = warps.stream()
                .sorted(Comparator.comparing(warp -> warp.id().name().value()))
                .toList();
    }

    public static LocationSnapshot empty() {
        return new LocationSnapshot(List.of(), List.of());
    }
}
