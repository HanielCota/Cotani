package com.cotani.nametag.internal;

import com.cotani.api.InternalApi;
import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.api.NametagProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Thread-safe registry and precedence resolver for nametags, overrides, and dynamic providers.
 *
 * <p>Encapsulates all in-memory nametag state management and resolution logic without scheduler dependencies.
 */
@InternalApi
public final class NametagRegistry {

    private final Map<UUID, Nametag> globalTags = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Nametag>> viewerOverrides = new ConcurrentHashMap<>();
    private final List<NametagProvider> providers = new CopyOnWriteArrayList<>();

    public void setGlobal(UUID playerId, Nametag nametag) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        Objects.requireNonNull(nametag, "Parameter 'nametag' must not be null");

        if (nametag.equals(Nametag.EMPTY)) {
            globalTags.remove(playerId);
            return;
        }
        globalTags.put(playerId, nametag);
    }

    public void removeGlobal(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        globalTags.remove(playerId);
    }

    public Optional<Nametag> getGlobal(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        return Optional.ofNullable(globalTags.get(playerId));
    }

    public void setOverride(UUID viewerId, UUID targetId, Nametag nametag) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");
        Objects.requireNonNull(nametag, "Parameter 'nametag' must not be null");

        if (nametag.equals(Nametag.EMPTY)) {
            removeOverride(viewerId, targetId);
            return;
        }

        viewerOverrides
                .computeIfAbsent(viewerId, _ -> new ConcurrentHashMap<>())
                .put(targetId, nametag);
    }

    public void removeOverride(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");

        var map = viewerOverrides.get(viewerId);
        if (map != null) {
            map.remove(targetId);
            if (map.isEmpty()) {
                viewerOverrides.remove(viewerId);
            }
        }
    }

    public void removeViewer(UUID viewerId) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        viewerOverrides.remove(viewerId);
    }

    public void removeAll(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        globalTags.remove(playerId);
        viewerOverrides.remove(playerId);
        for (var map : viewerOverrides.values()) {
            map.remove(playerId);
        }
    }

    public void registerProvider(NametagProvider provider) {
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        providers.add(provider);
    }

    public void unregisterProvider(NametagProvider provider) {
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        providers.remove(provider);
    }

    public Nametag resolveEffective(Player viewer, Player target) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(target, "Parameter 'target' must not be null");

        if (!providers.isEmpty()) {
            for (var provider : providers.reversed()) {
                try {
                    var customTag = provider.provideNametag(viewer, target);
                    if (customTag != null && customTag.isPresent()) {
                        return customTag.get();
                    }
                } catch (Exception exception) {
                    java.util.logging.Logger.getLogger(NametagRegistry.class.getName())
                            .log(java.util.logging.Level.WARNING, "Nametag provider failed", exception);
                }
            }
        }

        if (!viewerOverrides.isEmpty()) {
            var viewerMap = viewerOverrides.get(viewer.getUniqueId());
            if (viewerMap != null) {
                var override = viewerMap.get(target.getUniqueId());
                if (override != null) {
                    return override;
                }
            }
        }

        var global = globalTags.get(target.getUniqueId());
        if (global != null) {
            return global;
        }

        return Nametag.EMPTY;
    }

    public Nametag resolveEffective(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");

        var server = Bukkit.getServer();
        if (server != null) {
            var viewer = server.getPlayer(viewerId);
            var target = server.getPlayer(targetId);
            if (viewer != null && target != null) {
                return resolveEffective(viewer, target);
            }
        }

        if (!viewerOverrides.isEmpty()) {
            var viewerMap = viewerOverrides.get(viewerId);
            if (viewerMap != null) {
                var override = viewerMap.get(targetId);
                if (override != null) {
                    return override;
                }
            }
        }

        var global = globalTags.get(targetId);
        if (global != null) {
            return global;
        }

        return Nametag.EMPTY;
    }

    public Map<UUID, Nametag> globalTagsCopy() {
        return Map.copyOf(globalTags);
    }

    public void clear() {
        globalTags.clear();
        viewerOverrides.clear();
        providers.clear();
    }
}
