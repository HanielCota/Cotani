package com.cotani.redis;

/**
 * State machine representing the lifecycle of a {@link CotaniRedis} client instance.
 */
public enum RedisState {
    NEW,
    STARTING,
    CONNECTED,
    RECONNECTING,
    CLOSING,
    CLOSED,
    FAILED
}
