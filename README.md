# MotorSure — Motor Insurance Monorepo

MotorSure is a full-stack motor insurance starter using React, Spring Boot microservices, PostgreSQL, and Kafka.

## Repository layout

```text
MotorInsurance/
├── frontend/                     # React + Vite customer dashboard
├── backend/
│   ├── pom.xml                   # Shared Spring Boot parent, version management, and aggregator
│   └── services/
│       ├── quote-service/        # Quotes API (8081), publishes quote events
│       ├── policy-service/       # Policies API (8082)
│       └── claims-service/       # Claims API (8083)
└── environment/
    └── local/
        ├── docker-compose.yml    # Local PostgreSQL + Kafka
        ├── .env.example          # Local infrastructure settings
        └── postgres/init.sql     # Creates service databases
```

## Local development

### 1. Start environment services

```bash
cd environment/local
cp .env.example .env
docker compose up -d
```

PostgreSQL listens on port `5432`; Kafka listens on port `9092`. The init script creates `motor_quotes`, `motor_policies`, and `motor_claims` databases. The sample `.env` contains development credentials only.

### 2. Start backend services

From the repository root, package all services together:

```bash
cd backend
mvn package
```

Then run an individual service from its module directory:

```bash
cd backend/services/quote-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
cd backend/services/policy-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
cd backend/services/claims-service && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Each service applies its own Liquibase changelog automatically at startup, then Hibernate validates the mapped schema (`ddl-auto: validate`). Changelogs live under `src/main/resources/db/changelog/`. Add a new uniquely identified changeset for every schema change; do not edit a changeset that has already run in a shared environment. Set `SPRING_LIQUIBASE_ENABLED=false` only when migrations are managed by a separate deployment step. The base service configuration requires database credentials and Kafka bootstrap settings; `application-local.yml` provides development-only defaults.

| Service | Port | Database | Health |
| --- | ---: | --- | --- |
| Quote | 8081 | `motor_quotes` | `/actuator/health` |
| Policy | 8082 | `motor_policies` | `/actuator/health` |
| Claims | 8083 | `motor_claims` | `/actuator/health` |

The services currently expose `GET /api/{quotes|policies|claims}`, `GET /api/{quotes|policies|claims}/{id}`, and `POST` on each collection. Collection endpoints return Spring `Page` responses; use `?page=0&size=20&sort=createdAt,desc` to select a page.

### Shared dependency management

`backend/pom.xml` is the common Maven parent. It pins Spring Boot **4.1.1** in one place and inherits Spring Boot's dependency and plugin management. Child services omit dependency versions and declare only the starters they need. See [backend dependency conventions](/Users/raghavendra/Documents/MotorInsurance/backend/DEPENDENCIES.md) for the Initializr settings and the dependency choices for each service.

### 3. Start frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite development server runs at `http://localhost:5173`. API URLs can be overridden with `VITE_QUOTE_API_URL` and `VITE_CLAIMS_API_URL`.

## Current architecture

The frontend communicates with service REST APIs. Each backend service owns a separate database on the local PostgreSQL instance. The quote service records quote and outbox rows in one transaction; a background publisher sends pending `motor.quote.created` events to Kafka and retries after failures. Consumers should deduplicate by the event's `eventId`, since delivery is at least once. The dashboard currently uses sample policy data; policy issuance and claims workflows are early foundations and need further development.

The REST collections use Spring pagination (`page`, `size`, and `sort`) with a maximum page size of 100. Request validation errors and resource errors use RFC 9457 Problem Details responses. Database connection values can be overridden with `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `DB_POOL_*` environment variables; deployment configuration must also set `FRONTEND_ORIGIN`. Quote event publishing uses `KAFKA_BOOTSTRAP_SERVERS` and `OUTBOX_POLL_INTERVAL_MS`.

## Suggested next increments

1. Add an API gateway and OAuth2/OIDC authentication.
2. Build quote rating and policy purchase/issuance workflows.
3. Add Kafka consumers for cross-service workflows and define event schemas.
4. Add automated migration validation, service integration tests, observability, and deployment configuration.
