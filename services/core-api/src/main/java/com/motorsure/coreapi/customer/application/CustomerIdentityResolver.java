package com.motorsure.coreapi.customer.application;

import java.util.Optional;
import java.util.UUID;

/** Resolves only an exact validated issuer/subject pair; an absent mapping is always an authorization failure upstream. */
public interface CustomerIdentityResolver {
    Optional<UUID> resolveCustomerId(String issuer, String subject);
}
