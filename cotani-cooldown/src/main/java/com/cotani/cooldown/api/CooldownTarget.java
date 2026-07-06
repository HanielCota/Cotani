package com.cotani.cooldown.api;

public sealed interface CooldownTarget permits UserCooldownTarget, GlobalCooldownTarget, ResourceCooldownTarget {}
