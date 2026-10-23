# ADR 0001: Initial architecture and delivery approach

- Status: Accepted as the target direction; alignment with the current prototype is pending
- Date: 2026-10-24

## Context

The product needs insurance workflows with clear ownership of sensitive customer, policy, claim, payment, and document data. The repository already contains a working-scope prototype, but it uses three independent services, Spring Boot 4.1.1, Liquibase, and Kafka. The project context sets a different target baseline. Replacing those implementations without an explicit migration would risk existing work and data.

## Decisions

- Use Java 21, Spring Boot 3.5.x, and Maven for the target backend baseline. Verify the exact maintained 3.5.x patch and its compatible dependency versions before changing the current parent POM.
- Use React, TypeScript, and Vite for the web client.
- Use PostgreSQL as the transactional source of truth, with domain-owned tables and no cross-owner writes.
- Use Flyway for new schema migration work in the target baseline. Preserve existing Liquibase changelogs until a reviewed migration strategy is selected; never run both migration tools against the same schema without an explicit plan.
- Use Docker Compose for local development. The existing Compose file includes PostgreSQL and Kafka; target local infrastructure should change only as the messaging migration is approved.
- Start with a modular Core API rather than numerous independently deployable microservices. Preserve the current quote, policy, and claims services as a prototype until a consolidation plan is approved.
- Use Amazon SQS for future asynchronous workloads when a concrete workflow warrants it. Do not start a new Kafka deployment for the target architecture; the current Kafka outbox is prototype code and must be migrated deliberately.
- Target AWS ECS Fargate, RDS PostgreSQL, S3, and CloudWatch after security, data residency, backup, availability, and cost requirements are approved. Manage any eventual AWS resources as code; no cloud resources are provisioned in this milestone.
- Do not use Kubernetes for the initial deployment. A small number of managed components better fits the current scale and budget.
- Version HTTP/event contracts. Keep only schemas in libs/contracts; do not share domain entities or persistence code.
- Enforce server-side resource authorization and business state transitions. Use outbox publication for reliable asynchronous events, and make consumers duplicate-tolerant.
- Require automated tests and CI/CD for changes to the platform.
- Keep AI retrieval permission-aware and version-aware. AI output cannot independently approve underwriting, claims, or payments.

## Rationale

A modular Core API gives the team one deployable backend and one operational surface while preserving internal business boundaries. It avoids the network, monitoring, deployment, and on-call costs of many small services before independent scaling or ownership is needed. SQS is a managed, pay-per-use option for future asynchronous work; the existing Kafka implementation is not treated as the target messaging choice. ECS Fargate and RDS/S3/CloudWatch provide a later managed deployment path without introducing Kubernetes operations.

## Consequences

The architecture overview and service-boundaries document describe the target. The current code remains a transitional prototype and is not fully aligned. Do not claim the target migration is complete.

## Decisions requiring verification

- Exact supported Spring Boot 3.5.x patch and ecosystem compatibility
- Whether to consolidate the existing services before or after the first secure Core API workflow
- Flyway migration approach for databases with existing Liquibase history
- Identity provider, tenant model, and object-level authorization rules
- Product/rating versioning, currency model, audit retention, and document retention
- First SQS workflow, standard versus FIFO queues, idempotency keys, and outbox/inbox implementation
- AWS region, data residency, RDS sizing/backups, S3 retention/encryption, CloudWatch retention, and monthly cost ceiling
- AI provider, permitted data sources/tools, and human approval controls
