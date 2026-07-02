package com.cotani.teleport.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class TeleportPolicyChainTest {

    private static final TeleportContext CONTEXT = org.mockito.Mockito.mock(TeleportContext.class);

    @Test
    void allowsWhenAllPoliciesAllow() {
        var chain = new TeleportPolicyChain(List.of(ctx -> PolicyResult.allowed()));
        var result = chain.validate(CONTEXT);
        assertInstanceOf(PolicyResult.Allowed.class, result);
    }

    @Test
    void deniesWhenAnyPolicyDenies() {
        var chain = new TeleportPolicyChain(List.of(
                ctx -> PolicyResult.allowed(),
                ctx -> PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_PERMISSION, Component.text("no")),
                ctx -> PolicyResult.allowed()));
        var result = chain.validate(CONTEXT);
        assertInstanceOf(PolicyResult.Denied.class, result);
        assertEquals(TeleportFailureReason.BLOCKED_BY_PERMISSION, ((PolicyResult.Denied) result).reason());
    }

    @Test
    void shortCircuitsOnFirstDenial() {
        var first = new java.util.concurrent.atomic.AtomicBoolean();
        var second = new java.util.concurrent.atomic.AtomicBoolean();
        var chain = new TeleportPolicyChain(List.of(
                ctx -> {
                    first.set(true);
                    return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_PERMISSION, Component.text("no"));
                },
                ctx -> {
                    second.set(true);
                    return PolicyResult.allowed();
                }));
        chain.validate(CONTEXT);
        assertTrue(first.get());
        assertFalse(second.get());
    }

    @Test
    void emptyChainAllows() {
        var chain = new TeleportPolicyChain(List.of());
        var result = chain.validate(CONTEXT);
        assertInstanceOf(PolicyResult.Allowed.class, result);
    }
}
