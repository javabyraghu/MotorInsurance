# Core API foundation

## Scope

`services/core-api` is the first Spring Boot 3.5.x modular Core API foundation. It targets Java 21 and PostgreSQL. This milestone adds startup configuration, database migration readiness, actuator health/info, and `GET /api/v1/health`. It does not add insurance business behavior or schema tables.

The current quote, policy, and claims prototypes remain separate and unchanged. Their parent POM and Liquibase migrations are not part of this Core API build.

## Package direction

The application root package is `com.motorsure.coreapi`. Add domain packages only when their first real implementation is ready:

~~~text
com.motorsure.coreapi
├── api/health/       # HTTP health contract (present)
├── config/           # Cross-cutting Spring configuration (add with first need)
├── common/           # Shared primitives/error handling (add with first need)
├── customer/         # Customer capability (future)
├── product/          # Product capability (future)
├── quote/            # Quote capability (future)
├── underwriting/     # Underwriting capability (future)
└── policy/            # Policy capability (future)
~~~

Each business module should keep its API, application logic, domain model, and persistence code inside that module. Do not create empty package marker classes. Do not share JPA entities across modules.

## Configuration and operations

- `application.yml` reads connection details from `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. The password has no default outside the `local` profile.
- `application-local.yml` supplies localhost development defaults. Activate it explicitly with `SPRING_PROFILES_ACTIVE=local`.
- `ddl-auto` is `validate`; schema creation and changes belong in Flyway migrations under `src/main/resources/db/migration`.
- No migration is included yet because there are no business tables to create. Flyway may create its own `flyway_schema_history` metadata table when it first connects; no domain tables are created.
- Hibernate JDBC and Jackson use UTC. Graceful shutdown is enabled. Console logs use Spring Boot structured JSON output.
- Only actuator `health` and `info` are exposed over HTTP. Health details are hidden.
- No credentials, production secrets, cloud resources, Kafka, Redis, or AI provider configuration are included.

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
