package com.motorsure.coreapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.infrastructure.persistence.CustomerRepository;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CoreApiPostgresIntegrationTest.FixedClockConfiguration.class)
class CoreApiPostgresIntegrationTest {

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC); }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15-alpine")
            .withDatabaseName("motor_core")
            .withUsername("motorsure")
            .withPassword("integration-test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void clearCustomers() {
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from product_versions");
        jdbcTemplate.update("delete from products");
    }

    @Test
    void startsAgainstPostgresAndServesHealth() {
        var response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"service\":\"core-api\"");
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class)).isEqualTo("motor_core");
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo("motorsure");
        assertThat(flyway.info().applied()).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "select to_regclass('public.flyway_schema_history') is not null", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('customers', 'products', 'product_versions', 'product_version_coverages')
                """, Long.class)).isEqualTo(4L);
    }

    @Test
    void customerLifecycleUsesPostgresAndReturnsCorrelationMetadata() {
        String body = """
                {"firstName":"  Asha ","lastName":"Rao","email":" ASHA.RAO@EXAMPLE.TEST ",
                 "phoneNumber":"+1 415 555 0132","dateOfBirth":"1988-04-12"}
                """;
        var created = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                json(body), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
        assertThat(created.getBody()).contains("\"firstName\":\"Asha\"")
                .contains("\"email\":\"asha.rao@example.test\"")
                .contains("\"status\":\"ACTIVE\"");

        var fetched = restTemplate.getForEntity(created.getHeaders().getLocation(), String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).contains("CUS-");

        var updated = restTemplate.exchange(created.getHeaders().getLocation(), org.springframework.http.HttpMethod.PUT,
                json("""
                        {"firstName":"Asha","lastName":"Rao","email":"asha.new@example.test",
                         "phoneNumber":null,"dateOfBirth":"1988-04-12"}
                        """), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).contains("asha.new@example.test");

        String statusPath = created.getHeaders().getLocation().getPath() + "/status";
        var status = restTemplate.exchange(statusPath, org.springframework.http.HttpMethod.PATCH,
                json("{\"status\":\"INACTIVE\"}"), String.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).contains("\"status\":\"INACTIVE\"");
        assertThat(jdbcTemplate.queryForObject("select count(*) from customers", Long.class)).isEqualTo(1L);
    }

    @Test
    void rejectsFutureBirthDateAndInvalidPageSizeWithProblemDetails() {
        var future = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST, json("""
                {"firstName":"Sam","lastName":"Lee","email":"sam@example.test","dateOfBirth":"2026-01-02"}
                """), String.class);
        assertThat(future.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(future.getBody()).contains("request.validation_failed").contains("correlationId");
        var oversized = restTemplate.getForEntity("/api/v1/customers?size=101", String.class);
        assertThat(oversized.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var invalid = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST, json("""
                {"firstName":" ","lastName":"Lee","email":"not-an-email","phoneNumber":"123","dateOfBirth":"1990-01-01"}
                """), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).contains("invalidParams").doesNotContain("not-an-email");
        var malformed = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                json("{invalid json"), String.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody()).contains("request.malformed");
    }

    @Test
    void missingCustomerIsNotFoundAndListHasStablePageShape() {
        var missing = restTemplate.getForEntity("/api/v1/customers/00000000-0000-0000-0000-000000000001", String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("resource.not_found");
        var page = restTemplate.getForEntity("/api/v1/customers?page=0&size=20&sortBy=lastName&direction=ASC", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("\"items\":[]").contains("\"totalElements\":0");
    }

    @Test
    void postgresEnforcesCustomerNumberUniquenessAndOptimisticLocking() {
        var response = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                json("""
                        {"firstName":"Mina","lastName":"Shah","email":"mina@example.test","dateOfBirth":"1991-03-14"}
                        """), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(response.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        Customer firstRead = customerRepository.findById(id).orElseThrow();
        Customer staleRead = customerRepository.findById(id).orElseThrow();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        firstRead.updateProfile("Mina", "Shah", "one@example.test", null, LocalDate.of(1991, 3, 14), fixedClock);
        customerRepository.saveAndFlush(firstRead);
        staleRead.updateProfile("Mina", "Shah", "two@example.test", null, LocalDate.of(1991, 3, 14), fixedClock);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerRepository.saveAndFlush(staleRead))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        String number = customerRepository.findById(id).orElseThrow().getCustomerNumber();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into customers (id, customer_number, first_name, last_name, email, date_of_birth, status,
                                      created_at, updated_at, version)
                values (gen_random_uuid(), ?, 'Other', 'Person', 'other@example.test', '1990-01-01', 'ACTIVE', now(), now(), 0)
                """, number)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void productLifecyclePersistsCoverageVersionsAndEffectiveAvailability() {
        var product = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST, json("""
                {"productCode":"MOTOR-TP","name":"Motor Third Party","description":"Example catalog item","productType":"THIRD_PARTY"}
                """), String.class);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(product.getHeaders().getLocation()).isNotNull();
        assertThat(product.getBody()).contains("\"status\":\"DRAFT\"");
        String versionsPath = product.getHeaders().getLocation().getPath() + "/versions";
        var draft = restTemplate.exchange(versionsPath, org.springframework.http.HttpMethod.POST, json("""
                {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":"2027-01-01T00:00:00Z","coverages":[
                  {"code":"TP_LIABILITY","displayName":"Third-party liability","description":"Example only","coverageType":"THIRD_PARTY_LIABILITY","mandatory":true,"optional":false,"available":true},
                  {"code":"ROADSIDE","displayName":"Roadside assistance","coverageType":"ROADSIDE_ASSISTANCE","mandatory":false,"optional":true,"available":true}]}
                """), String.class);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(draft.getBody()).contains("\"versionNumber\":1").contains("\"status\":\"DRAFT\"");
        String versionId = draft.getHeaders().getLocation().getPath().replaceFirst(".*/", "");
        String activatePath = versionsPath + "/" + versionId + "/activate";
        var active = restTemplate.postForEntity(activatePath, null, String.class);
        assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(active.getBody()).contains("\"status\":\"ACTIVE\"");

        var available = restTemplate.getForEntity("/api/v1/products/available?at=2026-06-01T00:00:00Z", String.class);
        assertThat(available.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(available.getBody()).contains("MOTOR-TP");
        var outsideWindow = restTemplate.getForEntity("/api/v1/products/available?at=2027-01-01T00:00:00Z", String.class);
        assertThat(outsideWindow.getBody()).contains("\"totalElements\":0");

        var editActive = restTemplate.exchange(versionsPath + "/" + versionId, org.springframework.http.HttpMethod.PUT,
                json("""
                        {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":null,"coverages":[
                          {"code":"TP_LIABILITY","displayName":"Changed","coverageType":"THIRD_PARTY_LIABILITY","mandatory":true,"optional":false,"available":true}]}
                        """), String.class);
        assertThat(editActive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var secondDraft = restTemplate.exchange(versionsPath, org.springframework.http.HttpMethod.POST, json("""
                {"effectiveFrom":"2026-06-01T00:00:00Z","effectiveTo":null,"coverages":[
                  {"code":"OWN_DAMAGE","displayName":"Own damage","coverageType":"OWN_DAMAGE","mandatory":false,"optional":true,"available":true}]}
                """), String.class);
        assertThat(secondDraft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secondVersionId = secondDraft.getHeaders().getLocation().getPath().replaceFirst(".*/", "");
        var overlap = restTemplate.postForEntity(versionsPath + "/" + secondVersionId + "/activate", null, String.class);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var deactivated = restTemplate.postForEntity(activatePath.replace("/activate", "/deactivate"), null, String.class);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivated.getBody()).contains("\"status\":\"INACTIVE\"");
        var productNowInactive = restTemplate.getForEntity(product.getHeaders().getLocation(), String.class);
        assertThat(productNowInactive.getBody()).contains("\"status\":\"INACTIVE\"");
        assertThat(jdbcTemplate.queryForObject("select count(*) from product_version_coverages", Long.class)).isEqualTo(3L);
    }

    @Test
    void productValidationAndDuplicateCodeReturnSafeProblems() {
        String request = "{\"productCode\":\"SAFE-CODE\",\"name\":\"Safe\",\"productType\":\"COMPREHENSIVE\"}";
        var first = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST, json(request), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var duplicate = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST, json(request), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("request.conflict").doesNotContain("products_product_code_key");

        String productId = first.getHeaders().getLocation().getPath().replaceFirst(".*/", "");
        var invalidCoverage = restTemplate.exchange("/api/v1/products/" + productId + "/versions",
                org.springframework.http.HttpMethod.POST, json("""
                        {"effectiveFrom":"2026-01-01T00:00:00Z","coverages":[
                          {"code":"BAD","displayName":"Invalid","coverageType":"OTHER","mandatory":true,"optional":true,"available":true}]}
                        """), String.class);
        assertThat(invalidCoverage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidCoverage.getBody()).contains("request.validation_failed").contains("correlationId");
    }

    private org.springframework.http.HttpEntity<String> json(String body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
