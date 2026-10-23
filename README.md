# MotorSure — Motor Insurance Management Platform

MotorSure is being developed incrementally as a secure motor insurance management platform for customer profiles, insurance products, quotes, underwriting, policies, claims, payments, documents, notifications, and controlled AI assistance.

## Technology

| Area | Target direction | Current repository |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5.x, Maven | New Core API on Spring Boot 3.5.16; three preserved Spring Boot 4.1.1 prototype services |
| Web | React, TypeScript, Vite | React + TypeScript + Vite |
| Data | PostgreSQL, Flyway migrations | PostgreSQL, Liquibase migrations |
| Local development | Docker Compose | PostgreSQL + Kafka through Docker Compose |
| Messaging | Amazon SQS when an asynchronous workflow needs it | Kafka quote event outbox prototype |
| Cloud | AWS ECS Fargate, RDS PostgreSQL, S3, CloudWatch | No cloud resources provisioned |

The current service code and migrations are preserved. The target stack and current implementation are not yet aligned; resolving that is an explicit architecture milestone, not an assumed code migration. See [ADR 0001](docs/adr/0001-initial-architecture.md).

The existing backend includes service tests and the Core API has unit and Testcontainers integration coverage. No CI workflow is configured yet.

## Repository layout

~~~text
motor-insurance-platform/
├── apps/web/                       # React + TypeScript + Vite
├── services/
│   ├── pom.xml                     # Current shared Spring Boot parent and Maven aggregator
│   ├── quote-service/              # Existing quote prototype
│   ├── policy-service/             # Existing policy prototype
│   ├── claims-service/             # Existing claims prototype
│   ├── core-api/                   # Target modular API foundation (independent Maven build)
│   ├── customer-service/           # Planned; not runnable yet
│   └── ai-assistant-service/       # Planned; not runnable yet
├── libs/contracts/
│   ├── openapi/                    # Versioned HTTP API contracts
│   └── events/                     # Versioned event schemas
├── docs/
│   ├── architecture/
│   ├── adr/
│   └── runbooks/
├── infra/
│   ├── local/                      # Local-only PostgreSQL + Kafka Compose setup
│   └── aws/                        # Reserved; no infrastructure deployed
├── scripts/
├── .editorconfig
├── .gitignore
└── README.md
~~~

## Prerequisites

- Java 21
- Maven Wrapper for the Core API; Maven 3.9.6 or later for the existing prototype modules
- Node.js and npm (available environment: Node.js 21.6.2 and npm 10.2.4)
- Docker Engine with Docker Compose v2

AWS access, AWS credentials, a cloud account, and paid services are not required for local development.

## Current local setup

These commands run the existing prototype, not the future SQS/Flyway target architecture.

### Start local PostgreSQL and Kafka

~~~bash
cd infra/local
cp .env.example .env
docker compose up -d
~~~

The Compose file starts PostgreSQL on port 5432 and Kafka on port 9092. The PostgreSQL initialization script creates the three current prototype databases. The .env.example values are for local development only. Do not commit .env or credentials.

### Build and run the current backend

The Maven parent at services/pom.xml centralizes the current Spring Boot version and dependency management.

~~~bash
mvn -f services/pom.xml -DskipTests package
SPRING_PROFILES_ACTIVE=local mvn -f services/quote-service/pom.xml spring-boot:run
SPRING_PROFILES_ACTIVE=local mvn -f services/policy-service/pom.xml spring-boot:run
SPRING_PROFILES_ACTIVE=local mvn -f services/claims-service/pom.xml spring-boot:run
~~~

Run each service in a separate terminal. Each currently uses Liquibase at startup and Hibernate schema validation. Flyway is the target migration tool; existing Liquibase migrations are preserved pending the stack-alignment milestone.

### Start the current web app

~~~bash
cd apps/web
npm install
npm run dev
~~~

The web app runs at http://localhost:5173. Its quote and claims endpoints can be configured with VITE_QUOTE_API_URL and VITE_CLAIMS_API_URL.

For full local steps and shutdown guidance, see the [local development runbook](docs/runbooks/local-development.md).

## Core API foundation

The new `services/core-api` build is independent of the existing `services/pom.xml` prototype parent. It uses Java 21, Spring Boot 3.5.16, Maven Wrapper, PostgreSQL, and Flyway. It provides Customer Management, a Product catalog with versioned coverage configuration, and initial Quote Management, plus health endpoints, RFC 9457 error responses, and validated `X-Correlation-ID` propagation. Migrations V1–V8 create the customer, product, quote, and identity mapping structures, including currency precision and database-enforced immutable quote coverage snapshots. New quotes remain `NOT_CALCULATED`; no premium is stored or returned. The Core API validates JWTs and protects business routes with explicit scopes; customer self-service remains disabled until trusted identity provisioning and end-to-end ownership enforcement are implemented. No identity provider is selected, so deploy `JWT_ISSUER_URI` and `JWT_AUDIENCE` from your approved provider before using protected endpoints. See the [Customer](libs/contracts/openapi/customer-api.yaml), [Product](libs/contracts/openapi/product-api.yaml), and [Quote](libs/contracts/openapi/quote-api.yaml) OpenAPI contracts, [Core API package and lifecycle guidance](docs/architecture/core-api-foundation.md), and [persistence and API errors](docs/architecture/persistence-and-api-errors.md). The separate quote prototype and web integration are unchanged.

Start its isolated PostgreSQL container and Core API:

~~~bash
cp infra/local/core-api/.env.example infra/local/core-api/.env
docker compose --project-directory infra/local/core-api -f infra/local/core-api/docker-compose.yml up -d
~~~

The Core API PostgreSQL container maps to port 5433 by default and uses a separate named volume. It does not change the prototype PostgreSQL + Kafka stack. To run Core API checks, use `./services/core-api/mvnw -f services/core-api/pom.xml verify`; the PostgreSQL Testcontainers test is skipped if Docker is unavailable. See [Core API foundation](docs/architecture/core-api-foundation.md) for configuration and package guidance.

The application reads `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. The default profile requires these to be set for a real database connection; the explicit `local` profile supplies development-only localhost defaults. Protected routes remain inaccessible until `JWT_ISSUER_URI` and `JWT_AUDIENCE` are configured for a trusted issuer; local development does not provide a permissive or unsigned-token fallback. Set `APP_CORS_ALLOWED_ORIGINS` to explicit browser origins when cross-origin access is needed. To use any overrides from the Compose `.env` file, export them into the shell before starting the API:

~~~bash
set -a
. infra/local/core-api/.env
set +a
SPRING_PROFILES_ACTIVE=local ./services/core-api/mvnw -f services/core-api/pom.xml spring-boot:run
~~~

With PostgreSQL running and the local profile active, the custom endpoint returns `{"status":"UP","service":"core-api"}`:

~~~bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
~~~

Create and list individual customers:

These examples show the request shapes. The protected routes return `401` until you provide a bearer access token issued by the configured trusted provider and matching `JWT_AUDIENCE`; add `-H 'Authorization: Bearer <access-token>'` to the requests. This repository does not include a local token issuer or test-token endpoint.

~~~bash
curl -i -X POST http://localhost:8080/api/v1/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Asha","lastName":"Rao","email":"asha.rao@example.test","dateOfBirth":"1988-04-12"}'
curl 'http://localhost:8080/api/v1/customers?page=0&size=20&sortBy=createdAt&direction=DESC'
~~~

Create a draft Product, attach a versioned coverage configuration, then activate its version:

~~~bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"MOTOR-TP","name":"Motor Third Party","productType":"THIRD_PARTY"}'
curl -i -X POST http://localhost:8080/api/v1/products/{productId}/versions \
  -H 'Content-Type: application/json' \
  -d '{"effectiveFrom":"2026-01-01T00:00:00Z","coverages":[{"code":"TP_LIABILITY","displayName":"Third-party liability","coverageType":"THIRD_PARTY_LIABILITY","mandatory":true,"optional":false,"available":true}]}'
curl -X POST http://localhost:8080/api/v1/products/{productId}/versions/{versionId}/activate
curl 'http://localhost:8080/api/v1/products/available?at=2026-06-01T00:00:00Z'
~~~

Run all Core API checks, including the PostgreSQL Testcontainers tests (Docker required):

~~~bash
./services/core-api/mvnw -B -ntp -f services/core-api/pom.xml clean verify
~~~

## Planned development stages

1. Establish and validate the architecture and dependency baseline; reconcile the existing prototype with the target Spring Boot, migration, and messaging choices.
2. Build a secure Core API with modular customer, product, quote, underwriting, policy, claims, payment, document, and notification capabilities.
3. Complete OpenAPI validation, identity provisioning/ownership rules, audit history, and automated unit/integration tests with CI.
4. Add asynchronous workflows with SQS and transactional outbox/inbox handling where reliability or workload isolation requires them.
5. Add document storage and retention with S3 when the data classification and retention policy are agreed.
6. Add an AI assistant with permission-aware retrieval and tightly controlled actions; keep decisions and approvals in deterministic business workflows.
7. Deploy a small, cost-reviewed AWS footprint using ECS Fargate, RDS PostgreSQL, S3, and CloudWatch after deployment/security requirements are agreed.

Customer Management, Product catalog/versioning, initial Quote Management, and fail-closed JWT scope enforcement are implemented in the independent Core API. Trusted customer identity provisioning, customer self-service ownership enforcement, full pricing/underwriting/policy workflows, and cloud infrastructure remain future stages.

## Architecture and security

The target boundary and security principles are documented in the [architecture overview](docs/architecture/overview.md) and [service boundaries](docs/architecture/service-boundaries.md). Architecture decisions live in [docs/adr](docs/adr/), and operator procedures live in [docs/runbooks](docs/runbooks/).
