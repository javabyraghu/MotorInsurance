# ADR 0002: Existing service-boundary prototype

- Status: Historical prototype record; target direction is recorded in ADR 0001
- Date: 2026-10-24

## Context

An earlier starter implementation split quote, policy, and claims into independent Spring Boot modules with separate PostgreSQL databases. The quote service writes an outbox row with the quote, then publishes a Kafka event.

## Decision made for the prototype

Keep those three services separately runnable, give each its own database and Liquibase changelog, and place versioned event schemas under libs/contracts. The quote-created event uses at-least-once delivery and consumers are expected to deduplicate by event ID.

## Current status

The code and data boundaries remain in the repository and are preserved. This ADR describes the implementation, not the target deployment shape. ADR 0001 sets the target direction of a modular Core API, Spring Boot 3.5.x, Flyway for new target migrations, and SQS for future asynchronous workflows. Consolidation and technology migration require an explicit, reviewed plan.
