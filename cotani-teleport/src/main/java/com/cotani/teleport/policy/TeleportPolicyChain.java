package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportContext;
import java.util.Collection;
import java.util.List;

public final class TeleportPolicyChain {
    private final List<TeleportPolicy> policies;

    public TeleportPolicyChain(Collection<TeleportPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public PolicyResult validate(TeleportContext context) {
        for (TeleportPolicy policy : policies) {
            PolicyResult result = policy.validate(context);
            if (result instanceof PolicyResult.Denied) {
                return result;
            }
        }
        return PolicyResult.allowed();
    }

    public List<TeleportPolicy> policies() {
        return policies;
    }
}
