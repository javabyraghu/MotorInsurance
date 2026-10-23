# Persistence and API error conventions

These conventions apply to the Spring Boot 3.5 Core API at `services/core-api`. The current quote, policy, and claims prototype services retain their existing Liquibase configuration until a separately reviewed migration plan changes them.

## Database naming and types

- PostgreSQL is the transactional source of truth. Use unquoted, lowercase `snake_case` identifiers for schemas, tables, columns, indexes, and constraints. Avoid SQL reserved words. Use clear singular table names such as `insurance_product` and `policy`.
- Use PostgreSQL `uuid` for business entity identifiers and Java `UUID` in application code. Generate identifiers in the application unless a documented integration or data-import requirement needs another strategy.
- Use `timestamptz` for instants such as creation, effective, or received times. Map instants to `Instant` and keep the JDBC/Hibernate timezone at UTC. A local date such as a policyholder's date of birth is a `date`, not a timestamp.
- Persist monetary values with PostgreSQL `numeric(precision, scale)` selected for the specific business quantity and supported calculation range. Never use `real`, `float`, or `double precision` for money. Store an ISO 4217 currency code alongside each persisted monetary amount (for example, `currency_code varchar(3)`), and validate it against the supported currency list. Do not assume every monetary figure in an aggregate uses the same currency.
- Add `NOT NULL`, `CHECK`, `UNIQUE`, and foreign-key constraints when required by a real business invariant. Use foreign keys within a module's owned tables. Cross-module references normally use stable IDs and module application interfaces; add cross-module database constraints only through an explicit architecture decision.
- Add indexes for demonstrated access paths, uniqueness, or join requirements. Avoid speculative indexes, generic audit columns, and redundant constraints. Add audit history only when a workflow or retention requirement defines its purpose.

## Flyway migrations

Flyway SQL is the authoritative schema change mechanism for `core-api`. Hibernate is configured with `ddl-auto: validate`; it must never create, update, or drop production schema. Flyway validation is enabled and `flyway clean` is disabled in application configuration.

The current migration sequence is V1–V8: customer and product tables, quote/vehicle and coverage snapshot tables, declared currency precision, customer identity links, and quote snapshot immutability. V1–V8 are historical once applied and must not be edited. Future migrations must:

1. Add a versioned PostgreSQL SQL migration under `services/core-api/src/main/resources/db/migration/`, using `V<version>__<short_description>.sql`, for example `V4__add_product_audit_fields.sql`.
2. Keep a migration atomic where the database supports it, explicit, and limited to the owning module's schema objects. Include constraints in the same change as their invariant.
3. Treat applied migrations as immutable. Correct a deployed schema with a new forward migration; do not edit an already released migration to change an existing environment.
4. Review locking, table rewrites, and rollout compatibility for large or destructive changes. Separate expand and contract steps where old and new application versions may overlap.
5. Test against PostgreSQL with Testcontainers. Verify the resulting schema and Flyway validation, then test application persistence behavior. Do not use H2 as a substitute for PostgreSQL-specific migration checks.

Customer schema choices for V1: `customers` uses a UUID primary key, unique `customer_number`, required names/email/date of birth, optional phone, constrained status, `timestamptz` creation/update values, and a JPA optimistic-lock version. The `(created_at DESC, id ASC)` index supports the default ordered listing. Email and phone are deliberately not unique. V1 does not add a speculative email-search index.

Product schema choices for V2 and V3: product metadata, numbered versions, and coverage definitions are normalized into `products`, `product_versions`, and `product_version_coverages`. Version number and coverage code are unique within their owning parent. Effective ranges require end-after-start; activation serializes against the parent product and rejects overlapping active ranges. No monetary columns or jurisdiction-specific limits are defined.

Quote schema choices for V4–V6: quotes retain customer/product/version references, vehicle-risk values, and a relational coverage snapshot. Customer and product-version deletion is restricted while referenced. Declared vehicle value uses `numeric(16,4)` storage and is validated in the API against the JDK ISO 4217 minor-unit metadata for the supplied currency. V7 stores exact `(issuer, subject)` identity keys with a customer FK and a creation audit actor string; no provisioning workflow or email/phone matching is implemented. V8 rejects update/delete of quote coverage snapshots in PostgreSQL and restricts deleting their owning quote. No deployed database migration state was available during this audit; inspect each target's `flyway_schema_history` and rehearse migration/backups before rollout.

V4 intentionally adds cross-module foreign keys from Quote to Customer/Product-owned rows. This is an explicit Core API modular-monolith choice: the modules share one Flyway-managed PostgreSQL schema, and quote creation validates the references in the same transaction. The database constraints prevent orphaned references and product/version mismatches even if a future application path bypasses the service check. These constraints do not authorize a caller to access the referenced customer.

Flyway creates its own `flyway_schema_history` metadata table. This is migration bookkeeping, not a business table. The PostgreSQL integration test verifies the full migration sequence on a fresh database and checks key relational constraints.

`spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` are externalized as `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. Local development uses `application-local.yml` and the isolated PostgreSQL Compose setup. Production should inject credentials from its approved secret manager; do not put them in application YAML, source control, or command-line arguments captured in shell history.

## Module ownership and transactions

Each logical Core API module exclusively owns writes to its tables and defines its persistence operations. Do not let one module update another module's tables directly. Cross-module work uses the other module's public application interface and stable IDs. Quote creation delegates customer existence and product/version availability plus aggregate locking to Customer and Product application services; Quote persists only its own aggregate, vehicle-risk rows, and coverage snapshots.

When business operations are introduced, define transaction boundaries on application-service operations that represent one atomic use case. Keep transaction management out of web controllers. Use the outbox pattern only when a real committed change must be delivered asynchronously; it is not part of this foundation.

## RFC 9457 API errors

`ApiExceptionHandler` returns Spring `ProblemDetail` responses with `application/problem+json`. Every response includes `type`, `title`, `status`, `detail`, `instance`, stable `code`, and `correlationId`. Validation failures also include `invalidParams`, containing field names and safe annotation messages but no rejected values.

Representative status and code mappings:

| Condition | HTTP status | Code |
| --- | ---: | --- |
| Bean Validation failure | 400 | `request.validation_failed` |
| Malformed or unreadable request body | 400 | `request.malformed` |
| Other framework 400 errors | 400 | `request.invalid` |
| Missing resource | 404 | `resource.not_found` |
| Unsupported method | 405 | `request.method_not_allowed` |
| State or request conflict | 409 | `request.conflict` |
| Unsupported media type | 415 | `request.unsupported_media_type` |
| Unexpected server failure | 500 | `internal.error` |

Example validation response (the correlation ID is illustrative):

~~~json
{
  "type": "urn:motorsure:problem:validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/example",
  "code": "request.validation_failed",
  "correlationId": "7e99cb90-4935-4e28-a6d1-8f119c9577d5",
  "invalidParams": [
    { "name": "effectiveDate", "reason": "must be a future date" }
  ]
}
~~~

Framework exception details and unexpected exception messages are not copied to the response. Malformed JSON details, SQL, stack traces, Java class names, credentials, and submitted values are not returned. Unexpected failures are logged once in the advice with the exception type, HTTP method, matched route template, and correlation ID. A stack trace containing code locations is logged for server-side diagnosis, but the exception message/cause text, raw path, query string, and request body are omitted. Do not add duplicate logging in controllers or application services for the same failure.

## Request DTO validation

Put Bean Validation annotations on request DTO fields or record components (`@NotBlank`, `@Size`, `@Email`, and range/date constraints as appropriate), then annotate the controller request argument with `@Valid`. Keep messages concise, safe, and static; never interpolate the submitted value into a message. The centralized handler returns the same `request.validation_failed` envelope for all field errors.

The validation/error test controller lives only under `src/test/java`; it is not part of the running application or a business API endpoint.

## Correlation IDs and privacy

`CorrelationIdFilter` accepts `X-Correlation-ID` only when it matches `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. Missing or invalid values are replaced with a generated UUID. The chosen ID is returned in `X-Correlation-ID`, stored as a request attribute for error responses, and placed in SLF4J MDC for structured request logs. MDC is cleared in a `finally` block for every request.

A correlation ID is diagnostic metadata only. Never use it as identity, authorization evidence, a tenant key, or a trusted idempotency token. Do not log policyholder data, authentication tokens, secrets, or complete request bodies. Log route templates rather than raw URLs, which may contain sensitive path values.

## Verification

Run the Core API unit and PostgreSQL integration checks:

~~~bash
./services/core-api/mvnw -f services/core-api/pom.xml verify
~~~

The tests exercise health, malformed JSON, validation, missing resources, conflicts, unexpected failures, correlation ID validation/cleanup, application startup, database connectivity, and Flyway initialization. The PostgreSQL integration test uses Testcontainers and skips when Docker is unavailable. No external services are called.
