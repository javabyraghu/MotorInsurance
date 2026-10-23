# Service and module boundaries

## Initial deployment shape

Start with these as modules in a single Core API deployable. A module owns its model, application rules, persistence code, and database tables. Modules may call another module's public application interface inside the process, but must not write another module's tables directly.

| Capability | Initial Core API module responsibility | Data ownership |
| --- | --- | --- |
| Customer | Customer profile and references to authenticated identities | Contact/profile data and customer preferences |
| Product | Coverage options, eligibility configuration, and versioned product definitions | Product/rule configuration and effective dates |
| Quote | Rating inputs, quote snapshots, premium result, currency, and quote lifecycle | Quote request and immutable priced quote snapshot |
| Underwriting | Risk questions, evidence, referrals, decisions, and decision rationale | Underwriting case and decision record |
| Policy | Issuance, endorsements, renewal, cancellation, coverage dates, and policy documents references | Issued contract and coverage lifecycle |
| Claims | First notice of loss, evidence references, assessment, settlement decision, and claim state | Claim case and claim decision history |
| Payment | Premium/claim payment intent, provider references, reconciliation, and refund state | Payment ledger references and reconciliation state |
| Document | Upload/download authorization, metadata, versions, checksums, and retention | Document metadata and ownership; S3 owns document bytes |
| Notification | Delivery requests, templates, channel attempts, and delivery status | Notification request and delivery history |
| AI | Permission-aware retrieval and controlled, auditable assistance | Minimal conversation/task metadata; domain modules remain source of truth |

## Data ownership

- Give each module exclusive write ownership of its tables. During the modular-monolith phase, separate schemas or table prefixes can make ownership visible without operating separate database servers.
- Use stable IDs and explicit application interfaces for references across modules. Avoid ORM relationships and foreign keys that couple module internals.
- Do not copy a domain entity into a shared library. libs/contracts contains versioned API/event schemas only.
- External identity credentials belong to an identity provider. The Customer module stores the identity reference and customer profile, not passwords.
- Record monetary amounts with decimal precision and currency. Preserve the quoted price and applicable product/rating version on issued policies.

## Communication

Use in-process application interfaces and database transactions for synchronous work inside the initial Core API. Use authenticated HTTP APIs for the web client and future external integrations. Use SQS for asynchronous, retryable workflows such as notification delivery, document processing, and integration callbacks when those use cases are introduced. For changes that must publish after commit, persist an outbox record in the same transaction; consumers deduplicate by event ID and make handlers idempotent.

Do not use an event for a request that needs a synchronous validation/decision before responding. Do not allow consumers to bypass the module that owns a business decision.

## What may become an independent service later

Keep Customer, Product, Quote, Underwriting, Policy, Claims, and Payment as Core API modules initially. Extract a capability only when measured scaling, isolation, regulatory controls, availability, release cadence, or team ownership justifies the network and operational cost.

Likely extraction candidates after workflows are stable are Notification delivery, Document processing/storage integration, and the AI assistant. Payment integration or high-volume quote rating may also be extracted if vendor isolation or measured load warrants it. These are options, not commitments.

## Existing prototype mapping

The repository currently has separate quote, policy, and claims Spring Boot deployables with separate PostgreSQL databases; the quote service publishes through Kafka. Preserve them as the current prototype until a migration plan is approved. Customer and AI assistant service directories are placeholders. Consolidating services, introducing the remaining modules, changing Liquibase to Flyway, and replacing Kafka with SQS are future work, not part of the foundation milestone.
