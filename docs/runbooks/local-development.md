# Local development runbook

This runbook covers the current prototype. It does not provision AWS resources.

## Prerequisites

Java 21, Maven, Node.js/npm, and Docker Engine with Docker Compose v2.

## Start the local dependencies

From the repository root:

~~~bash
cp infra/local/.env.example infra/local/.env
docker compose --project-directory infra/local -f infra/local/docker-compose.yml up -d
docker compose --project-directory infra/local -f infra/local/docker-compose.yml ps
~~~

The Compose project starts PostgreSQL and Kafka. The PostgreSQL initialization script creates the motor_quotes, motor_policies, and motor_claims databases when PostgreSQL initializes a fresh data volume. Editing the initialization script does not rerun it against an existing volume.

## Build and run the services

Build the current Maven modules:

~~~bash
mvn -f services/pom.xml -DskipTests package
~~~

Run each service in a separate terminal from the repository root:

~~~bash
SPRING_PROFILES_ACTIVE=local mvn -f services/quote-service/pom.xml spring-boot:run
SPRING_PROFILES_ACTIVE=local mvn -f services/policy-service/pom.xml spring-boot:run
SPRING_PROFILES_ACTIVE=local mvn -f services/claims-service/pom.xml spring-boot:run
~~~

Current migrations use Liquibase. The Flyway target described in ADR 0001 has not been applied to these databases.

## Run the web app

~~~bash
cd apps/web
npm install
npm run dev
~~~

Vite listens on http://localhost:5173 by default.

## Stop local dependencies

~~~bash
docker compose --project-directory infra/local -f infra/local/docker-compose.yml down
~~~

This stops containers and preserves the named PostgreSQL volume. Do not add -v unless you intentionally want to delete local database data.

## Current limitations

The local Compose setup is a prototype with development-only credentials and plaintext Kafka. Do not expose it to an untrusted network or reuse these credentials outside local development. Authentication, object-level authorization, production secrets, AWS deployment, and SQS are not configured yet.
