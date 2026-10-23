package com.motorsure.coreapi.customer.application;

import com.motorsure.coreapi.customer.infrastructure.persistence.CustomerIdentityLinkRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only resolver. It deliberately has no account-linking or profile-attribute matching operation. */
@Service
@Transactional(readOnly = true)
public class PersistedCustomerIdentityResolver implements CustomerIdentityResolver {
    private final CustomerIdentityLinkRepository repository;

    public PersistedCustomerIdentityResolver(CustomerIdentityLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> resolveCustomerId(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIssuerAndSubject(issuer, subject).map(link -> link.getCustomerId());
    }
}
