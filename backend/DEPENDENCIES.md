# Spring Boot dependency conventions

The backend parent POM is the single source of truth for the Spring Boot release and managed dependency versions. It uses the latest stable Spring Boot version offered by Spring Initializr when this update was made: **4.1.1**.

## Generate a new service with Spring Initializr

On [start.spring.io](https://start.spring.io), select:

- Project: Maven
- Language: Java
- Spring Boot: 4.1.1 (latest stable at update time)
- Java: 21
- Dependencies required by that service, for example Spring Web, Validation, Spring Data JPA, Liquibase Migration, Spring Boot Actuator, Spring for Apache Kafka, and PostgreSQL Driver

The Initializr dependency called **Spring Web** maps to `spring-boot-starter-webmvc` on Spring Boot 4. For JSON APIs, that starter brings the JSON support used by Spring MVC. Jackson 3 is Boot 4's default JSON mapper; custom code should use the `tools.jackson.*` APIs. The quote service explicitly includes `spring-boot-starter-json` for its outbox serialization.

## Add the generated service to this monorepo

1. Move the generated project under `backend/services/<service-name>`.
2. Replace its generated Spring Boot parent with the shared parent:

   ```xml
   <parent>
     <groupId>com.motorsure</groupId>
     <artifactId>motor-insurance-backend</artifactId>
     <version>0.1.0</version>
     <relativePath>../../pom.xml</relativePath>
   </parent>
   ```

3. Add its directory to `<modules>` in `backend/pom.xml`.
4. Keep only dependencies the new service actually uses. Do not add dependency versions in service POMs; they are managed centrally by the parent.
5. Add `spring-boot-starter-test` with `test` scope for unit and integration tests. Add `spring-boot-starter-cache` only to a service when its cache provider, consistency, and invalidation behavior are defined.

## Dependency selection currently in use

| Capability | Dependency | Current use |
| --- | --- | --- |
| REST / MVC | `spring-boot-starter-webmvc` | All services |
| Bean validation | `spring-boot-starter-validation` | All services |
| JPA / Hibernate | `spring-boot-starter-data-jpa` | All services |
| Schema migrations | `spring-boot-starter-liquibase` | All services |
| Health and metrics endpoints | `spring-boot-starter-actuator` | All services |
| Kafka | `spring-boot-starter-kafka` | Quote service |
| Jackson JSON | `spring-boot-starter-json` | Quote service explicitly; also provided through the MVC stack |
| Tests | `spring-boot-starter-test` | All services, test scope |
| Caching | `spring-boot-starter-cache` | Managed by the parent, add to a service when needed |
| PostgreSQL JDBC | `org.postgresql:postgresql` | All services, runtime scope |
