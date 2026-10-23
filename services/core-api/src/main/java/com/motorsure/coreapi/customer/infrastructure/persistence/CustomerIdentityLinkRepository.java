package com.motorsure.coreapi.customer.infrastructure.persistence;

import com.motorsure.coreapi.customer.domain.CustomerIdentityLink;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerIdentityLinkRepository extends JpaRepository<CustomerIdentityLink, UUID> {
    Optional<CustomerIdentityLink> findByIssuerAndSubject(String issuer, String subject);
}
