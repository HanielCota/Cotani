package com.cotani.event.api;

/**
 * Listener execution order.
 *
 * <p>MONITOR runs last and should be used only for read-only observation,
 * logging or metrics.</p>
 */
public enum EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR
}
