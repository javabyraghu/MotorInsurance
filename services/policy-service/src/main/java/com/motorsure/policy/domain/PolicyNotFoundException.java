package com.motorsure.policy.domain;

import java.util.UUID;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(UUID id) { super("Policy not found: " + id); }
}
