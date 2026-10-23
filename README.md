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

The existing backend has test dependencies but no checked-in automated tests or CI workflow yet. The web app has not been built as part of this foundation milestone.

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
│   ├── openapi/                    # Versioned HTTP API contracts (to be added)
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

The new `services/core-api` build is independent of the existing `services/pom.xml` prototype parent. It uses Java 21, Spring Boot 3.5.16, Maven Wrapper, PostgreSQL, and Flyway. It currently exposes `GET /api/v1/health` and actuator health/info endpoints; business modules and schema migrations will be added as their first real workflows are implemented.

Start its isolated PostgreSQL container and Core API:

~~~bash
cp infra/local/core-api/.env.example infra/local/core-api/.env
docker compose --project-directory infra/local/core-api -f infra/local/core-api/docker-compose.yml up -d
~~~

The Core API PostgreSQL container maps to port 5433 by default and uses a separate named volume. It does not change the prototype PostgreSQL + Kafka stack. To run Core API checks, use `./services/core-api/mvnw -f services/core-api/pom.xml verify`; the PostgreSQL Testcontainers test is skipped if Docker is unavailable. See [Core API foundation](docs/architecture/core-api-foundation.md) for configuration and package guidance.

The application reads `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. The default profile requires these to be set for a real database connection; the explicit `local` profile supplies development-only localhost defaults. To use any overrides from the Compose `.env` file, export them into the shell before starting the API:

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

## Planned development stages

1. Establish and validate the architecture and dependency baseline; reconcile the existing prototype with the target Spring Boot, migration, and messaging choices.
2. Build a secure Core API with modular customer, product, quote, underwriting, policy, claims, payment, document, and notification capabilities.
3. Add contract-first API documentation, authorization, audit history, and automated unit/integration tests with CI.
4. Add asynchronous workflows with SQS and transactional outbox/inbox handling where reliability or workload isolation requires them.
5. Add document storage and retention with S3 when the data classification and retention policy are agreed.
6. Add an AI assistant with permission-aware retrieval and tightly controlled actions; keep decisions and approvals in deterministic business workflows.
7. Deploy a small, cost-reviewed AWS footprint using ECS Fargate, RDS PostgreSQL, S3, and CloudWatch after deployment/security requirements are agreed.

The Core API foundation is now present as a separate build, while the later stages remain plans. Its business modules, production identity/authorization, cloud infrastructure, and domain schema migrations are not implemented yet.

## Architecture and security

The target boundary and security principles are documented in the [architecture overview](docs/architecture/overview.md) and [service boundaries](docs/architecture/service-boundaries.md). Architecture decisions live in [docs/adr](docs/adr/), and operator procedures live in [docs/runbooks](docs/runbooks/).
