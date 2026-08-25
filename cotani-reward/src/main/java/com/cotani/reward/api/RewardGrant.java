package com.cotani.reward.api;

/** Immutable value that a host plugin can settle after a claim succeeds. */
public sealed interface RewardGrant permits CurrencyGrant, ItemGrant {}
