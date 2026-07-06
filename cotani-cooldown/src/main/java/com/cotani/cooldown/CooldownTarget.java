package com.cotani.cooldown;

public sealed interface CooldownTarget permits UserCooldownTarget, GlobalCooldownTarget, ResourceCooldownTarget {}
