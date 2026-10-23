# Core API foundation

## Authentication and authorization

The Core API uses Spring Security OAuth 2.0 Resource Server JWT validation. No identity provider is selected by this repository. Deployments must configure `JWT_ISSUER_URI` and `JWT_AUDIENCE`; the configured issuer must publish trusted OIDC discovery metadata/JWK signing keys. Tokens are signature-checked against those keys, issuer-checked, and validated for expiry/not-before using Spring Security's standard validators, plus an exact audience match. Only the `scope` claim maps to `SCOPE_<scope>` authorities; the alternate `scp` claim is not consumed. Never put signing keys or credentials in this repository.

If either JWT setting is absent, the application may start for health/diagnostics, but protected API routes have no token decoder and cannot authenticate callers. This is a closed-by-default mode, not a local authentication shortcut. Automated tests use a fixed, test-only RSA key and a test issuer/audience without contacting an identity provider. If both settings are supplied but the issuer metadata cannot be loaded or is invalid, startup fails instead of accepting tokens without validation. Production must use HTTPS and a trusted issuer reachable for metadata/JWK retrieval.

Authorization policy:

| API operation | Required authority |
| --- | --- |
| `GET /api/v1/customers`, `GET /api/v1/customers/{id}` | `SCOPE_customers:read` |
| Customer create/update/status changes | `SCOPE_customers:write` |
| Product catalog, availability, product, and version GET routes | `SCOPE_products:read` |
| Product creation, updates, version lifecycle | `SCOPE_products:write` |
| `GET /api/v1/quotes`, `GET /api/v1/quotes/{id}` | `SCOPE_quotes:read` |
| Quote create/submit/cancel | `SCOPE_quotes:write` |
| `/api/v1/health`, `/actuator/health/**` | Public liveness/readiness only |
| `/actuator/info` | `SCOPE_core:ops` |

All other non-OPTIONS routes are denied. CORS preflight OPTIONS requests pass the authorization matcher and are then subject to the explicit CORS origin/method/header policy. These authorities are intended for explicitly provisioned support, operations, and product-administration callers. Customer and quote APIs are not customer self-service: `customers:self` and `quotes:self` are not accepted. Quote IDs and `customerId` filters are only usable with operations scopes. Migration V7 adds a dedicated `customer_identity_links` boundary keyed by the exact validated JWT issuer and subject, with a customer foreign key and link-creation audit fields. A read-only resolver returns no customer for missing or unmapped identities. There is deliberately no link-creation API or automatic matching by email, phone, name, or other mutable profile fields. The table alone does not establish that any mapping was properly provisioned and does not enable self-service. Self-service must remain denied until a trusted provisioning policy exists and ownership is enforced end to end for profile retrieval and quote list, retrieval, creation, submission, and cancellation. The identity provider, scope assignment policy, account proofing, link operators, issuer-change handling, and unlink/revocation audit policy remain deployment/business decisions.

Requests are stateless bearer-token requests. CSRF is disabled because the API does not authenticate with browser cookies or server sessions. CORS accepts no cross-origin browser origins by default; configure an explicit comma-separated `APP_CORS_ALLOWED_ORIGINS` allowlist where needed. Wildcard origins are rejected, and credentials are never sent through CORS. TLS termination and secure forwarded-header configuration belong to the deployment boundary. Authentication failures and denials return sanitized RFC 9457 responses and preserve `X-Correlation-ID`; tokens and authorization headers are not written by application code to logs. The custom `GET /api/v1/health` endpoint reports process liveness only. Actuator `/actuator/health/readiness` includes Spring's readiness state and PostgreSQL connectivity; deployment probes should use that readiness path and `/actuator/health/liveness` for liveness.

## Scope

`services/core-api` is the Spring Boot 3.5.x modular Core API. It targets Java 21 and PostgreSQL. Its current business capabilities are individual Customer Management, a versioned Product catalog, and initial Quote Management, backed by Flyway migrations V1–V8, alongside actuator health/info and `GET /api/v1/health`.

The current quote, policy, and claims prototypes remain separate and unchanged. Their parent POM and Liquibase migrations are not part of this Core API build.

## Package direction

The application root package is `com.motorsure.coreapi`. Add domain packages only when their first real implementation is ready:

~~~text
com.motorsure.coreapi
├── api/health/       # HTTP health contract (present)
├── common/web/       # Problem responses and correlation IDs (present)
├── common/validation/ # Shared clock-aware validation
├── customer/         # Individual customer capability (present)
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/persistence/
├── product/          # Product catalog and versioned coverage capability (present)
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/persistence/
├── quote/            # Initial quote lifecycle and immutable coverage snapshot
├── underwriting/     # Underwriting capability (future)
└── policy/            # Policy capability (future)
~~~

Each business module keeps its API, application logic, domain model, and persistence code inside that module. Customer exposes no JPA entity in HTTP responses. Its HTTP contract is `libs/contracts/openapi/customer-api.yaml`. Customer endpoints follow the authorization policy above. Do not create empty package marker classes or share JPA entities across modules.

## Customer domain decisions

- This first slice supports individuals only. Names are trimmed and limited to 100 characters. Email is validated for syntax, trimmed, and lowercased with `Locale.ROOT`; duplicate email is allowed because no uniqueness requirement has been established.
- Phone is optional and is not unique. Input accepts digits and common international punctuation, with 7–15 digits and a maximum of 32 characters; this does not validate assignment or country-specific numbering plans.
- Date of birth is required and cannot be in the future. No minimum-age, eligibility, underwriting, or product rules are inferred.
- The server generates a UUID and derives a non-sequential public number (`CUS-` plus uppercase UUID hex). The unique database constraint detects any collision and maps it to a safe conflict response.
- `ACTIVE` is the initial status. Status changes are explicit. `@Version` protects writes from lost updates.
- Confirm business retention rules for date of birth, accepted phone formats, customer-number presentation, email case policy, and lifecycle states before broader rollout. Address and government identity data are intentionally not collected.

## Configuration and operations

- `application.yml` reads connection details from `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. The password has no default outside the `local` profile.
- `application-local.yml` supplies localhost development defaults. Activate it explicitly with `SPRING_PROFILES_ACTIVE=local`.
- `ddl-auto` is `validate`; schema creation and changes belong in Flyway migrations under `src/main/resources/db/migration`.
- Flyway migration `V1__create_customers.sql` creates the initial `customers` table. Applied migrations are immutable.
- Product migrations V2 and V3 create the product catalog and normalized version/coverage tables.
- V7 creates `customer_identity_links`. `(issuer, subject)` is unique as an exact pair, so identical subjects from different issuers remain distinct. Customer deletion is restricted while a link exists. `linked_at` and opaque `linked_by` record link creation; tokens and unrelated JWT claims are never stored. No unlink operation exists yet, so future revocation must retain auditable history and must not reassign an identity silently.
- Provisioning decisions remain open: the authorized linking actor, onboarding versus administrative workflow, proof that a person controls the internal account, approval/separation-of-duties requirements, issuer migration behavior, and revocation/retention policy. Do not populate mappings by matching email or phone. The current migration establishes uniqueness and referential integrity but does not claim an operator identity is authenticated or that a link is verified.
- Hibernate JDBC and Jackson use UTC. Graceful shutdown is enabled. Console logs use Spring Boot structured JSON output.
- Only actuator `health` and `info` are exposed over HTTP. Health details are hidden.
- No credentials, production secrets, cloud resources, Kafka, Redis, or AI provider configuration are included.

## Product catalog lifecycle

The Product module stores controlled product types, a stable immutable product code, product lifecycle status, numbered configuration versions, effective date windows, and relational coverage definitions. Product types and coverage types include `OTHER` so examples such as third-party and comprehensive are configurable values, not embedded rating rules. A version starts as `DRAFT`; draft versions can be edited. Application operations make an activated configuration immutable: every current application write path (draft product edits, version creation/editing, activation, and deactivation) goes through `ProductService`, and all version operations lock the owning Product row. Quote eligibility takes that same lock through quote commit. There is no database trigger protecting active versions or coverage rows from direct SQL. This is an accepted boundary for the current architecture because the application service is the catalog write interface; adding triggers would duplicate lifecycle rules and complicate authorized lifecycle updates without preventing a privileged database owner from disabling the trigger. The production runtime credential must be held only by the Core API, human access must not use that credential, and catalog data changes outside the API must use an approved, audited migration/administrative process. Deployments that permit routine direct DML need database-level enforcement and audit controls before release. Version activation locks the owning product and rejects overlapping active intervals using half-open `[effectiveFrom, effectiveTo)` ranges. Deactivation is terminal for that version; follow-on configuration is created as a new version. Product availability can be queried through `GET /api/v1/products/available` at a requested instant (current UTC by default).

Coverage rows intentionally contain no limits, deductibles, rates, or eligibility rules. Currency, measurement units, jurisdiction-specific required cover, and renewal requirements need business confirmation. The module makes no regulatory-compliance claim. Product endpoints follow the authorization policy above.

## Quote management initial slice

The Quote module is part of the Core API and owns the `quotes`, `quote_vehicle_risk`, and `quote_coverage_snapshots` tables. V4 introduces quotes and vehicle-risk records, V5 adds the relational coverage snapshot, V6 widens declared vehicle value storage to support currencies with up to four fractional digits, and V8 makes persisted coverage snapshots immutable in PostgreSQL. Quotes retain customer, product, and exact product-version UUID references. A composite foreign key enforces that the selected version belongs to the selected product, and customer/product/version deletion is restricted while referenced.

Quote creation calls the Customer and Product application services rather than accessing their repositories directly. The Product application service locks its aggregate root using the same lock used by lifecycle writers, so concurrent deactivation cannot pass between availability validation and quote creation. The instant defaults to the injected server `Clock`; active date ranges use `[effectiveFrom, effectiveTo)`, with a null end unbounded. Product coverage rows are copied transactionally into a quote-owned snapshot and are returned with the quote. PostgreSQL rejects snapshot updates and deletes; later catalog lifecycle changes do not rewrite the snapshot.

Quotes start in `DRAFT` with `pricingStatus=NOT_CALCULATED` and a null `expiresAt`. `POST /api/v1/quotes/{id}/submit` moves a draft to `PENDING_UNDERWRITING`; this is only a workflow label and does not mean an underwriting service or assessment exists. Draft and pending quotes can be cancelled; cancelled quotes are terminal. Repeated submit/cancel actions are not idempotent: once a transition is no longer valid, the API returns `409 Conflict`. Optimistic locking protects concurrent state changes, which also return a conflict. There is no premium column or premium response field, pricing, expiry scheduler, acceptance, approval, decline, policy issuance, or event publishing. The old quote prototype and frontend integration remain unchanged.

Vehicle categories, usage classifications, field requirements, jurisdiction support, data retention, and future access/ownership rules require business confirmation. Declared value is optional and must be paired with a supported Java ISO 4217 currency code; it is positive and validated against that currency's minor-unit precision, with up to four fractional digits stored and no market-value or eligibility inference. Currency codes are normalized to uppercase. Registration is optional and trimmed without other normalization. Quote endpoints require the operations scopes described above; customer self-service remains denied until identity mapping and record-level ownership checks are implemented. Quote request bodies and registration numbers must not be logged.

## Local PostgreSQL

The Core API has an isolated PostgreSQL-only Compose setup at `infra/local/core-api/docker-compose.yml`. It uses a named volume and maps PostgreSQL to port 5433 by default, leaving the existing prototype Compose stack on port 5432. Local credentials are development-only.

~~~bash
cp infra/local/core-api/.env.example infra/local/core-api/.env
docker compose --project-directory infra/local/core-api -f infra/local/core-api/docker-compose.yml up -d
set -a
. infra/local/core-api/.env
set +a
SPRING_PROFILES_ACTIVE=local ./services/core-api/mvnw -f services/core-api/pom.xml spring-boot:run
~~~

Stop the container with `docker compose --project-directory infra/local/core-api -f infra/local/core-api/docker-compose.yml down`. This preserves its named volume; do not add `-v` unless you intend to delete local database data.

## Verification

Run the Core API unit and integration tests with:

~~~bash
./services/core-api/mvnw -f services/core-api/pom.xml verify
~~~

The PostgreSQL integration test uses Testcontainers and is skipped when Docker is unavailable. Start Docker to run it. The web slice test does not require a database.
