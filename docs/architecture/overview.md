# Architecture overview

## Business scope

The platform will support customer and vehicle references, product configuration, quote calculation, underwriting, policy issuance and servicing, claims, payment references, document metadata/storage, notifications, and controlled AI assistance. Insurance rating and claim/payment approval remain explicit business workflows.

## Target initial architecture

Start with a modular Core API and a React + TypeScript web application. Keep business capabilities as separately owned application modules in one backend deployable while workflows, team size, and operational needs are still being established. Use PostgreSQL as the transactional source of truth. Keep module boundaries and data ownership strict even when modules share a PostgreSQL server.

Use synchronous, authenticated APIs for user-facing reads and commands that need an immediate result. Introduce Amazon SQS for durable background work and cross-boundary events when asynchronous processing has a clear benefit. Use an outbox when a committed database change must reliably publish a message; consumers must be idempotent because delivery can be duplicated.

Store document bytes in S3 when the document workflow and retention requirements are defined; store metadata, ownership, versions, checksums, and access policy in the owning module. AI retrieval must filter by the requesting user's authorization and document version. AI-generated content is advisory; agents cannot bypass server-side business rules or approve claims, underwriting decisions, or payments.

## Current repository implementation

The repository contains a React + TypeScript app, three preserved Spring Boot prototype services for quote, policy, and claims, and an independent Spring Boot 3.5 Core API with Customer, Product, initial Quote, JWT scope authorization, and Flyway migrations. The prototypes use Spring Boot 4.1.1, PostgreSQL, Liquibase, and a Kafka quote outbox; their previous service database ownership remains separate from the Core API's PostgreSQL/Flyway ownership. Customer identity provisioning, customer self-service authorization, and production deployment are not complete.

The three original prototype services are preserved and do not yet match the target stack or deployment shape. The Core API uses Spring Boot 3.5.x and Flyway, but SQS, service consolidation, trusted identity provisioning, customer self-service ownership enforcement, and cloud deployment have not happened. Do not infer production readiness from the Core API's JWT route protection alone.

## Budget-conscious deployment direction

For local development, use Docker Compose and no paid services. For an eventual AWS deployment, first estimate a small footprint: one Core API task (scale out only when necessary), a small RDS PostgreSQL instance, S3 for documents, and CloudWatch. Use SQS only for real asynchronous work. Avoid always-on service proliferation and defer Kubernetes. Do not provision resources until cost, security, availability, backup, and data residency requirements are approved.

## Architecture principles

- A business module owns its data; no direct cross-module or cross-service database writes.
- PostgreSQL is the transactional source of truth.
- Monetary values use decimal arithmetic and an explicit currency. The prototype quote model currently does not persist a currency field; this must be addressed before real rating or policy sales.
- Version and document public API and event contracts. OpenAPI contracts belong in libs/contracts/openapi/; event schemas belong in libs/contracts/events/.
- Validate business state transitions on the server and enforce authorization at the object/resource level on every request.
- Use an outbox for reliable database-to-message publication and make consumers tolerate duplicate delivery.
- AI retrieval respects authorization, source version, and retention; agents cannot bypass business rules or independently approve claims or payments.
- Keep secrets out of source control. Manage cloud infrastructure as code.
- Automated unit/integration testing and CI/CD are part of the architecture, not a later quality add-on.

## Decisions still requiring verification

Select the exact supported Spring Boot 3.5.x patch and confirm dependency compatibility; decide the identity provider and authorization model; define audit/retention and document classification; confirm the first SQS use case and delivery/deduplication policy; and cost-review AWS sizing before deployment.
