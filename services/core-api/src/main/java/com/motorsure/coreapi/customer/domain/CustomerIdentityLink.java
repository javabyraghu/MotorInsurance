package com.motorsure.coreapi.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A trusted association provisioned by an authorized workflow; profile attributes are never used to create it. */
@Entity
@Table(name = "customer_identity_links")
public class CustomerIdentityLink {
    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String issuer;

    @Column(nullable = false, columnDefinition = "text")
    private String subject;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "linked_by", nullable = false, updatable = false, columnDefinition = "text")
    private String linkedBy;

    protected CustomerIdentityLink() { }

    public UUID getId() { return id; }
    public String getIssuer() { return issuer; }
    public String getSubject() { return subject; }
    public UUID getCustomerId() { return customerId; }
    public Instant getLinkedAt() { return linkedAt; }
    public String getLinkedBy() { return linkedBy; }
}
