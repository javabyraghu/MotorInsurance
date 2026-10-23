# Quote module migration and implementation plan

**Assessment date:** 2026-10-24
**Repository revision inspected:** `main` at `61ca7574081a93f818adf9d921868b0e9f476d04` (`origin/main`)
**Scope:** Prepare the Core API for Quote Management. This document does not implement it.

## Confidence and scope of findings

Facts below were verified in the checked-out source, configuration, and Git history. The repository does not prove what has been deployed, which local database volumes contain applied Liquibase changes, or whether Kafka consumers exist outside this repository. Those items are marked as unknown and must be confirmed before any runtime cutover or data migration.

## Current Core API architecture

The Core API is an independent Maven build at `services/core-api`, using Java 21, Spring Boot 3.5.16, PostgreSQL, JPA, and Flyway. It is not a module in `services/pom.xml`; that parent still builds the separate Spring Boot 4.1.1 prototypes. Core API business modules follow `com.motorsure.coreapi.<module>/{api,application,domain,infrastructure/persistence}` and share RFC 9457 errors, correlation IDs, and a controllable `Clock` bean.

The checked-in Core API migrations are:

| Version | File | Objects |
| --- | --- | --- |
| V1 | `V1__create_customers.sql` | `customers` |
| V2 | `V2__create_products.sql` | `products` |
| V3 | `V3__create_product_versions_and_coverages.sql` | `product_versions`, `product_version_coverages` |

Accordingly, **V4 is the next repository migration version** for a Core API Quote schema. The integration suite applies migrations to a fresh PostgreSQL Testcontainer. Actual version state in any persistent or deployed database has not been inspected; check each target environment's `flyway_schema_history` before rollout. Core API uses `ddl-auto: validate`, Flyway validation, `flyway clean` disabled, and `open-in-view: false`. Its POM has no Kafka, Liquibase, or Jackson 3 dependencies.

Local Core API Compose uses a separate named volume and PostgreSQL port 5433, default database `motor_core`. The prototype Compose setup uses a different named volume and PostgreSQL port 5432; its initialization script creates `motor_quotes`, `motor_policies`, and `motor_claims`. Both can run beside one another locally. Environment variables can override either service's JDBC URL, so database separation is a convention of the checked-in local setup, not a guarantee for deployments.

## Verified legacy Quote prototype

`services/quote-service` is a separately executable Spring Boot service on port 8081. It is included in the `services/pom.xml` Maven aggregator and has its own `services/quote-service/pom.xml`. That POM inherits Spring Boot 4.1.1 through the prototype parent and includes MVC, validation, JPA, Liquibase, Actuator, Kafka, Jackson JSON, PostgreSQL, and test dependencies. It is not part of the Core API build.

The prototype provides `POST /api/quotes`, `GET /api/quotes`, and `GET /api/quotes/{id}`. Its request stores a customer name, vehicle registration, optional make, and a string cover type. The JPA `Quote` stores those fields, an annual premium, a string status, creation time, and an optimistic-lock version. It has no customer ID, product ID, product-version ID, coverage snapshot, quote number, expiry, or vehicle risk model beyond registration and make. The only initialized status is `READY`; there are no lifecycle transition endpoints. No test sources are checked in under `services/quote-service`.

`QuoteService.create` assigns a fixed placeholder premium of 7,200.00 for third-party or 14,680.00 for comprehensive cover, then persists the quote and records a created event. The source comment explicitly identifies the tariff as a placeholder. The persisted premium has numeric precision, but no currency is stored; it must not be represented as a real or saleable price.

The prototype Liquibase changelog has two change sets:

1. `quote-001-create-quotes` creates `quotes` with customer name, registration, make, cover type, `annual_premium NUMERIC(12,2)`, `status` (default `READY`), creation timestamp, and optimistic-lock version. It adds a nonnegative-premium check and indexes for creation time and vehicle registration.
2. `quote-002-create-outbox` creates `outbox_events` with event ID, aggregate type/ID, event type, text payload, creation time, and nullable publication time.

In the checked-in prototype, `QuoteService.create` is transactional. `QuoteEventPublisher` writes a `QuoteCreated` event to `outbox_events` in that transaction. The scheduled publisher polls up to 50 unpublished rows, locks them using `FOR UPDATE SKIP LOCKED`, synchronously waits up to 15 seconds for Kafka sends, and marks them published in a transaction. A failed batch is rolled back and retried later. Because Kafka publication and the PostgreSQL transaction do not share one atomic commit, a send acknowledged before a later database rollback can be delivered again; consumers need event-ID-based deduplication. The event goes to `motor.quote.created` and contains customer name, vehicle registration/make, cover type, placeholder annual premium, and creation time. The checked-in JSON Schema at `libs/contracts/events/motor.quote.created/v1.schema.json` describes this v1 payload and has no currency or product-version snapshot.

## Existing references, overlap, and compatibility

Repository search found these direct dependencies on the prototype:

- `apps/web/src/App.tsx` submits its quote form to `${VITE_QUOTE_API_URL || 'http://localhost:8081'}/api/quotes` using the prototype request shape. On network failure it still reports a local “saved” message, which is not proof of persistence.
- `services/pom.xml` includes `quote-service` as a build module.
- `README.md` and `docs/runbooks/local-development.md` document how to run it.
- The event schema and outbox publisher reference the `motor.quote.created` topic.
- The only checked-in OpenAPI files describe Customer and Product; there is no Quote contract yet.

No event consumer or other repository code using `motor.quote.created` was found. This does not establish that there are no consumers outside the repository. The prototype database is configured as `motor_quotes` locally, while the Core API defaults to `motor_core`; however, externally supplied `DATABASE_URL` values could point both applications at one database.

Potential conflicts to manage:

- **Routes:** the existing client uses `/api/quotes`; the intended Core API route is `/api/v1/quotes`. These can coexist during development, but a deliberate frontend cutover is required. Avoid dual-write behavior.
- **Schema ownership:** the old service runs Liquibase and owns `quotes` and `outbox_events`; Core API owns Flyway migrations V1–V3. Never let both migration tools own the same tables or point an unbaselined Core API Flyway instance at a database with an incompatible history. Keep the Core API on its dedicated database/schema. Do not edit old Liquibase changesets.
- **Table naming/data:** both systems naturally use a `quotes` table name. Sharing a database would create a table/ownership collision. The old row cannot be losslessly converted: it lacks a customer reference, exact product/version identity, risk inputs, snapshot, and currency. Its premium is an explicitly fixed placeholder and `READY` does not mean rated.
- **Build/runtime:** reusing the old service's framework/configuration inside the Core API would cross Spring Boot 4.1.1 and 3.5.16 baselines. Its JSON event code uses Jackson 3 `tools.jackson.*`, while the Core API currently uses its Boot 3.5 dependency set. Copy business concepts selectively; do not add dependencies or migrate the old service as part of quote-module work.
- **Events:** the v1 topic/schema conveys a price but no currency, product-version identity, or real pricing provenance. Any existing external consumer compatibility must be discovered before publishing, changing, or retiring that contract.
- **Frontend:** switching to the new contract requires changing the submitted data from customer name/cover label to customer ID, product/version selection, and vehicle inputs. Update the user-facing success/error behavior so it cannot imply a quote was saved or priced when the API did not confirm that result.

## Recommended Core API model

Implement a new `com.motorsure.coreapi.quote` module with `api`, `application`, `domain`, and `infrastructure.persistence` packages. The Quote module owns its tables. It uses the existing Customer and Product application/repository interfaces and shared error handling; it does not write their tables directly. Because these modules currently share the Core API PostgreSQL database, customer/product existence checks and quote/snapshot inserts can run within one application-service transaction without network calls.

Proposed aggregate and persistence fields:

| Data | Proposed representation |
| --- | --- |
| Identity | Application-generated UUID `id`; unique server-generated non-sequential `quote_number` (for example, `QTE-` plus uppercase UUID hex). |
| References | Required `customer_id`, `product_id`, and exact `product_version_id`. Retain product and version identity on the quote; enforce that the selected version belongs to the selected product with a composite foreign key or equivalent database constraint. |
| Vehicle risk input | A typed, validated vehicle-risk record owned by the quote: initially consider registration, vehicle class/type, make, model, model year, fuel type, engine displacement with explicit unit (cc), intended use, and jurisdiction/region code. Confirm which factors are needed and legally appropriate before finalizing fields. Do not infer eligibility or rating rules from these attributes. Avoid copying customer names/contact details into the quote. Apply access, retention, and logging controls to registration and other identifying inputs. |
| Product-version snapshot | Immutable relational snapshot rows for each coverage definition copied from the exact selected active product version at quote creation. Capture stable code, display name, coverage type, mandatory/optional/available flags, and the source version ID. Keep the source reference even though the copied snapshot preserves what the quote saw. Do not snapshot configured limits or money until their types, units, currencies, and business semantics are defined. |
| Lifecycle | Controlled enum such as `DRAFT`, `PENDING_PRICING`, `QUOTED`, `ACCEPTED`, `DECLINED`, `EXPIRED`, and `CANCELLED`; transitions are explicit application-service operations, with terminal states defined. `QUOTED` is only entered after an actual calculation result exists. No policy is issued by this module. |
| Pricing | Separate `pricing_status`, initially **`NOT_CALCULATED`**. No fabricated zero or fixed amount. Any later calculated amount must use exact decimal storage and carry explicit ISO currency and pricing/rating version. Enforce that price and currency are absent while not calculated and both present when calculated. |
| Expiry | Nullable `expires_at timestamptz` until an actual quote is priced and an expiry rule is approved; set it with the quote result. Enforce acceptance only before expiry. Expiry automation/clock behavior is a later, testable use case. |
| Concurrency/time | `@Version` optimistic lock, consistent with Customer/Product. Use injected `Clock` for created/updated times, requested coverage instant defaults if approved, and expiry checks. Use `timestamptz`/`Instant` for instants and documented timezone behavior. |

**Unconfirmed business assumptions:** vehicle-risk data, retention period, jurisdictions and supported vehicle classes, quote validity duration, whether expiry starts at quote creation or calculation, customer ownership/authorization, coverage selection semantics, and any later pricing inputs or rules. No age, eligibility, tariff, regulatory, or underwriting rules are proposed here.

## Proposed API surface

Use the versioned Core API route `/api/v1/quotes`, DTOs (never JPA entities), the existing pagination maximum of 100, sort allowlists, RFC 9457 errors, and `X-Correlation-ID` propagation. Proposed endpoints:

- `POST /api/v1/quotes` — create a draft quote from `customerId`, `productId`, `productVersionId`, intended coverage effective instant, and typed vehicle-risk data. Server creates the quote number and immutable coverage snapshot; pricing remains `NOT_CALCULATED`.
- `GET /api/v1/quotes/{id}` — retrieve quote state and its persisted snapshot.
- `GET /api/v1/quotes?customerId=…&page=0&size=20` — list quotes with explicitly supported filters and stable sorting; do not expose unrestricted dynamic filtering.
- Explicit action routes such as `POST /{id}/submit`, `/accept`, `/decline`, and `/cancel` only when their transitions and actors are agreed. Do not allow arbitrary client status assignment. `accept` is invalid until status is `QUOTED`, pricing is calculated, and the quote is unexpired. No endpoint in this module issues a policy.

On create, the application service should:

1. Validate DTO syntax and vehicle-risk bounds without echoing values in errors/logs.
2. Load/verify the customer using the Customer module's public interface. Return a safe `404` if absent; authorization/customer ownership is not implemented today and must be added before exposing personal quote data.
3. Load the product and exact product version. Verify their relationship, both lifecycle statuses are active, and the version's half-open interval contains the quote's intended coverage effective instant: `effectiveFrom <= instant < effectiveTo`, with null `effectiveTo` unbounded. If a version is not effective, return a stable conflict or validation response chosen and documented in the API contract.
4. Within one Core API application-service transaction, insert the quote and copy the selected version's coverage definitions into the immutable snapshot. Use module interfaces and repository reads; never update Customer/Product tables directly.
5. Return `201 Created`, `Location`, DTO response, and correlation header. No price is displayed as if it were valid.

## Migration, pricing, and prototype disposition

The Core API should remain the sole future source of truth. For a dedicated Core API database with V1–V3 recorded, start the quote schema at **V4**. A planned first quote migration could introduce `quotes` and a second migration could add `quote_vehicle_risk` and `quote_coverage_snapshots`; choose table boundaries after model review. Use UUIDs, `snake_case`, `timestamptz`, foreign keys to Customer/Product-owned tables where module ownership permits, check constraints for enums and pricing-state consistency, unique `quote_number`, and indexes for demonstrated lookup paths (customer, status/expiry, creation time). Use a composite product/version constraint so mismatched references cannot persist. No float/double values; do not create an amount column with zero defaults. Keep applied V1–V3 immutable and verify migrations against PostgreSQL Testcontainers.

Do not run Liquibase and Flyway against the same quote tables. Keep the legacy Liquibase schema and Core API Flyway schema separate through development and cutover. Do not dual-write quotes. Before cutover, verify each environment's database URL, schema owners, Liquibase history, Flyway history, backup/retention requirements, and any deployed consumers. The current prototype rows should not be silently converted to new quotes: without reliable customer/product/version/risk/currency/pricing provenance, the safe default is to retain/expire/archive them as legacy records under an approved retention plan. If business requires import, define an explicit reviewed mapping, mark imported records as unverified legacy data, preserve original IDs/premium/status separately, and prevent them from being accepted or issued as newly priced quotes.

To avoid placeholder premiums being mistaken for prices:

- The Core API starts every new quote at `pricing_status=NOT_CALCULATED` and has no calculated amount/currency.
- Never copy the prototype's fixed premium values into Core API records or present the old `READY` status as `QUOTED`.
- Persist a real amount only after a separately approved, versioned rating calculation succeeds, together with currency and rating provenance. Until then responses and UI copy must say not calculated/pending rather than show a price.
- Treat `motor.quote.created/v1` as the legacy prototype contract. Do not emit it for new quotes without a reviewed compatibility decision. If quote events are required, agree on a new versioned schema carrying quote ID/number, customer reference policy, product/version identity, coverage snapshot reference, pricing status, amount/currency only when valid, and privacy constraints. Search found no in-repository consumer; verify external consumers and plan deduplication before changing topic/payload behavior.

Do not delete, rename, or modify the current prototype in this preparation step. Recommended future disposition: keep it operating only until a reviewed UI/API cutover; point the web application at Core API after its functionality, auth, and failure messaging are ready; disable new writes to the prototype; verify/retain its database and event history according to business retention requirements; then remove it from the Maven aggregator and stop its deployment in a separate approved decommission change. Do not leave both endpoints active as independent quote sources of truth.

## Recommended implementation sequence

1. Confirm vehicle-risk fields, selected coverage semantics, expiry policy, quote state transitions, data retention, ownership/authorization, and any external event consumers.
2. Review the schema/API contract and confirm every target database is dedicated to Core API Flyway ownership with V1–V3 applied; establish a backup and no-data-loss plan.
3. Add the Quote domain, controlled statuses/pricing status, typed DTOs, application service, repository, global-error-compatible endpoints, and V4+ migrations. Validate customer/product/version relationships and availability transactionally; snapshot coverage configuration.
4. Add deterministic domain/service tests with fixed Clock and PostgreSQL Testcontainers tests for migrations, constraints, effective date boundaries, snapshot persistence, optimistic locking, and correlation/problem responses. Run the full Core API suite.
5. Publish the quote OpenAPI contract and update architecture docs. Keep the web app and prototype unchanged until the Core API path is fully reviewed.
6. Implement a separately reviewed frontend/API cutover, any new versioned event contract/consumer migration, and legacy data retention or archival. Only after cutover validation, decommission the prototype in a separate change.

No Quote entities, migrations, controllers, dependencies, infrastructure, runtime changes, or prototype changes are part of this assessment.
