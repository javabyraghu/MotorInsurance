# Current prototype dependency notes

These notes describe the existing prototype only. The services parent POM currently manages Spring Boot 4.1.1, Liquibase, and Kafka. The target direction in [ADR 0001](../docs/adr/0001-initial-architecture.md) is Spring Boot 3.5.x, Flyway for new target migrations, and SQS for future asynchronous workflows. Do not use this page as approval to add more independent services or migrate existing schemas.

## Existing prototype baseline

The current modules use Maven and Java 21. The Spring Boot version is managed by the parent POM. The exact target Spring Boot 3.5.x patch and dependency set still require verification before implementation.

The existing Boot 4 modules use spring-boot-starter-webmvc; Jackson 3 APIs are used by quote event serialization. These Boot 4-specific dependency details may change when the target baseline is adopted.

## Adding modules

Do not add a new deployable service until its workflow and operational boundary are approved. If adding a module to the existing prototype is later approved:

1. Place it under services/<service-name>.
2. Inherit the current prototype parent:

~~~xml
<parent>
  <groupId>com.motorsure</groupId>
  <artifactId>motor-insurance-backend</artifactId>
  <version>0.1.0</version>
  <relativePath>../pom.xml</relativePath>
</parent>
~~~

3. Add its directory to the modules list in services/pom.xml.
4. Keep only dependencies the module actually uses. Do not add dependency versions in service POMs; they are managed centrally by the parent.
5. Add test dependencies and cache support only when the module scope and cache consistency/invalidation behavior are defined.

## Current prototype dependency selection

| Capability | Dependency | Current use |
| --- | --- | --- |
| REST / MVC | spring-boot-starter-webmvc | All services |
| Bean validation | spring-boot-starter-validation | All services |
| JPA / Hibernate | spring-boot-starter-data-jpa | All services |
| Schema migrations | spring-boot-starter-liquibase | All services |
| Health and metrics endpoints | spring-boot-starter-actuator | All services |
| Kafka | spring-boot-starter-kafka | Quote service |
| Jackson JSON | spring-boot-starter-json | Quote service explicitly; also provided through MVC |
| Tests | spring-boot-starter-test | Test scope is configured; automated tests are not yet checked in |
| Caching | spring-boot-starter-cache | Not currently used |
| PostgreSQL JDBC | org.postgresql:postgresql | All services, runtime scope |
