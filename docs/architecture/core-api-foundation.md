# Core API foundation

## Scope

`services/core-api` is the Spring Boot 3.5.x modular Core API. It targets Java 21 and PostgreSQL. Its initial business capabilities are individual Customer Management and a versioned Product catalog, backed by Flyway migrations V1–V3, alongside actuator health/info and `GET /api/v1/health`.

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
├── quote/            # Quote capability (future)
├── underwriting/     # Underwriting capability (future)
└── policy/            # Policy capability (future)
~~~

Each business module keeps its API, application logic, domain model, and persistence code inside that module. Customer exposes no JPA entity in HTTP responses. Its HTTP contract is `libs/contracts/openapi/customer-api.yaml`. Authentication and authorization are not implemented yet. Do not create empty package marker classes or share JPA entities across modules.

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
- Hibernate JDBC and Jackson use UTC. Graceful shutdown is enabled. Console logs use Spring Boot structured JSON output.
- Only actuator `health` and `info` are exposed over HTTP. Health details are hidden.
- No credentials, production secrets, cloud resources, Kafka, Redis, or AI provider configuration are included.

## Product catalog lifecycle

The Product module stores controlled product types, a stable immutable product code, product lifecycle status, numbered configuration versions, effective date windows, and relational coverage definitions. Product types and coverage types include `OTHER` so examples such as third-party and comprehensive are configurable values, not embedded rating rules. A version starts as `DRAFT`; draft versions can be edited. Activation makes its configuration immutable. Version activation locks the owning product and rejects overlapping active intervals using half-open `[effectiveFrom, effectiveTo)` ranges. Deactivation is terminal for that version; follow-on configuration is created as a new version. Product availability can be queried through `GET /api/v1/products/available` at a requested instant (current UTC by default).

Coverage rows intentionally contain no limits, deductibles, rates, or eligibility rules. Currency, measurement units, jurisdiction-specific required cover, and renewal requirements need business confirmation. The module makes no regulatory-compliance claim. Authentication and authorization remain unimplemented.

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
