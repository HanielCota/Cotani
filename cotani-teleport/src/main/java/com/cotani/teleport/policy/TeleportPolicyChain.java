package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportContext;
import java.util.List;

public record TeleportPolicyChain(List<TeleportPolicy> policies) {

    public TeleportPolicyChain {
        policies = List.copyOf(policies);
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
}
