package com.motorsure.coreapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.infrastructure.persistence.CustomerRepository;
import com.motorsure.coreapi.customer.application.CustomerIdentityResolver;
import com.motorsure.coreapi.quote.domain.Quote;
import com.motorsure.coreapi.quote.infrastructure.persistence.QuoteRepository;
import com.motorsure.coreapi.quote.application.QuoteService;
import com.motorsure.coreapi.quote.api.QuoteRequests;
import com.motorsure.coreapi.quote.domain.VehicleCategory;
import com.motorsure.coreapi.quote.domain.FuelType;
import com.motorsure.coreapi.quote.domain.UsageCategory;
import com.motorsure.coreapi.product.application.ProductService;
import com.motorsure.coreapi.product.domain.ProductType;
import com.motorsure.coreapi.product.domain.ProductVersion;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import com.motorsure.coreapi.security.TestJwtConfiguration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({CoreApiPostgresIntegrationTest.FixedClockConfiguration.class, TestJwtConfiguration.class})
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

    @Autowired
    private CustomerIdentityResolver customerIdentityResolver;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void clearCustomers() {
        if (restTemplate.getRestTemplate().getInterceptors().stream().noneMatch(i -> i instanceof CoreApiAuthInterceptor)) {
            restTemplate.getRestTemplate().getInterceptors().add(new CoreApiAuthInterceptor(
                    bearerToken("customers:read customers:write products:read products:write quotes:read quotes:write core:ops")));
        }
        jdbcTemplate.execute("TRUNCATE customer_identity_links, quote_coverage_snapshots, quote_vehicle_risk, quotes, "
                + "product_version_coverages, product_versions, products, customers CASCADE");
    }

    @Test
    void resolvesOnlyExactIssuerSubjectPairsAndPostgresEnforcesIdentityAndCustomerConstraints() {
        UUID customerA = createCustomer("identity-a@example.test", "+1 415 555 0132");
        UUID customerB = createCustomer("identity-b@example.test");
        Instant linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        insertIdentity(UUID.randomUUID(), "https://issuer-a.example.test/", "shared-subject", customerA, linkedAt);
        insertIdentity(UUID.randomUUID(), "https://issuer-b.example.test/", "shared-subject", customerB, linkedAt);
        insertIdentity(UUID.randomUUID(), TestJwtConfiguration.ISSUER, "caller-a", customerA, linkedAt);

        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-a.example.test/", "shared-subject"))
                .contains(customerA);
        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-b.example.test/", "shared-subject"))
                .contains(customerB);
        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-a.example.test/", "missing"))
                .isEmpty();
        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-a.example.test/", "identity-a@example.test"))
                .isEmpty();
        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-a.example.test/", "+1 415 555 0132"))
                .isEmpty();
        assertThat(customerIdentityResolver.resolveCustomerId("https://issuer-a.example.test/", ""))
                .isEmpty();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertIdentity(UUID.randomUUID(),
                "https://issuer-a.example.test/", "shared-subject", customerB, linkedAt))
                .isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from customers where id = ?", customerA))
                .isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertIdentity(UUID.randomUUID(),
                "https://issuer-c.example.test/", "unmapped", UUID.randomUUID(), linkedAt))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(scopedExchangeAs("/api/v1/customers/" + customerB, org.springframework.http.HttpMethod.GET,
                null, "customers:self", "caller-a").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchangeAs("/api/v1/quotes?customerId=" + customerB, org.springframework.http.HttpMethod.GET,
                null, "quotes:self", "caller-a").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchangeAs("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                "{\"customerId\":\"" + customerB + "\"}", "quotes:self", "caller-a").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID createCustomer(String email) {
        return createCustomer(email, null);
    }

    private UUID createCustomer(String email, String phone) {
        Customer customer = customerRepository.save(Customer.register(UUID.randomUUID(), "Test", "Customer", email,
                phone, LocalDate.of(1985, 1, 1), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)));
        return customer.getId();
    }

    private void insertIdentity(UUID id, String issuer, String subject, UUID customerId, Instant linkedAt) {
        jdbcTemplate.update("""
                insert into customer_identity_links (id, issuer, subject, customer_id, linked_at, linked_by)
                values (?, ?, ?, ?, ?, ?)
                """, id, issuer, subject, customerId, java.sql.Timestamp.from(linkedAt), "test:operator");
    }

    @Test
    void enforcesBearerAuthenticationScopesAudienceAndCorrelationErrors() {
        var authInterceptors = restTemplate.getRestTemplate().getInterceptors();
        var automaticAuth = authInterceptors.stream().filter(i -> i instanceof CoreApiAuthInterceptor).findFirst().orElseThrow();
        authInterceptors.remove(automaticAuth);
        var anonymousHeaders = new org.springframework.http.HttpHeaders();
        anonymousHeaders.set("X-Correlation-ID", "security-test-1");
        var anonymous = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(anonymousHeaders), String.class);
        authInterceptors.add(automaticAuth);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(anonymous.getBody()).contains("security.authentication_required");
        assertThat(anonymous.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("security-test-1");
        assertThat(anonymous.getHeaders().getFirst("Set-Cookie")).isNull();

        var wrongScope = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers("quotes:read")), String.class);
        assertThat(wrongScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongScope.getBody()).contains("security.access_denied");

        var scpOnlyHeaders = new org.springframework.http.HttpHeaders();
        scpOnlyHeaders.setBearerAuth(bearerTokenWithScpOnly("products:read"));
        var scpOnly = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(scpOnlyHeaders), String.class);
        assertThat(scpOnly.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var validProductRead = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers("products:read")), String.class);
        assertThat(validProductRead.getStatusCode()).isEqualTo(HttpStatus.OK);

        var readOnlyWrite = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>("{}", headers("customers:read")), String.class);
        assertThat(readOnlyWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var invalidAudience = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headersWithAudience("products:read", "some-other-api")), String.class);
        assertThat(invalidAudience.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalidAudience.getBody()).doesNotContain("some-other-api");

        var invalidSignatureHeaders = new org.springframework.http.HttpHeaders();
        String tamperedToken = corruptSignature(bearerToken("products:read"));
        invalidSignatureHeaders.setBearerAuth(tamperedToken);
        var invalidSignature = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(invalidSignatureHeaders), String.class);
        assertThat(invalidSignature.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalidSignature.getBody()).doesNotContain(tamperedToken);
        String unsignedToken = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + TestJwtConfiguration.ISSUER + "\",\"aud\":\"" + TestJwtConfiguration.AUDIENCE
                        + "\",\"sub\":\"unsigned\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond()
                        + ",\"scope\":\"products:read\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".";
        var unsignedHeaders = new org.springframework.http.HttpHeaders();
        unsignedHeaders.setBearerAuth(unsignedToken);
        var unsigned = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(unsignedHeaders), String.class);
        assertThat(unsigned.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unsigned.getBody()).doesNotContain(unsignedToken);

        var wrongIssuer = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headersFor("products:read", "https://untrusted.invalid", Instant.now().plusSeconds(600))), String.class);
        assertThat(wrongIssuer.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var expired = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headersFor("products:read", TestJwtConfiguration.ISSUER, Instant.now().minusSeconds(120))), String.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var notYetValid = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headersNotBefore("products:read", Instant.now().plusSeconds(120))), String.class);
        assertThat(notYetValid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var selfServiceScopes = headers("quotes:self customers:self");
        var quoteId = "00000000-0000-0000-0000-000000000001";
        for (var request : java.util.List.of(
                restTemplate.exchange("/api/v1/quotes/" + quoteId, org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(selfServiceScopes), String.class),
                restTemplate.exchange("/api/v1/quotes?customerId=" + quoteId, org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(selfServiceScopes), String.class),
                restTemplate.exchange("/api/v1/quotes/" + quoteId + "/submit", org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>(selfServiceScopes), String.class),
                restTemplate.exchange("/api/v1/quotes/" + quoteId + "/cancel", org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>(selfServiceScopes), String.class))) {
            assertThat(request.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        var publicHealth = restTemplate.getRestTemplate().getForEntity("/api/v1/health", String.class);
        assertThat(publicHealth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicHealth.getHeaders().getFirst("Set-Cookie")).isNull();
    }

    @Test
    void authorizationMatrixCoversCustomerProductQuoteOperationalAndUnknownRoutes() {
        String customerBody = """
                {"firstName":"Route","lastName":"Matrix","email":"route.matrix@example.test","dateOfBirth":"1990-01-01"}
                """;
        var createdCustomer = scopedExchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                customerBody, "customers:write");
        assertThat(createdCustomer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String customerPath = createdCustomer.getHeaders().getLocation().getPath();
        UUID customerId = UUID.fromString(customerPath.substring(customerPath.lastIndexOf('/') + 1));
        assertThat(scopedExchange("/api/v1/customers", org.springframework.http.HttpMethod.GET, null, "customers:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(customerPath, org.springframework.http.HttpMethod.GET, null, "customers:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(customerPath, org.springframework.http.HttpMethod.PUT,
                "{\"firstName\":\"Route\",\"lastName\":\"Updated\",\"email\":\"route.updated@example.test\",\"dateOfBirth\":\"1990-01-01\"}",
                "customers:write").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(customerPath + "/status", org.springframework.http.HttpMethod.PATCH,
                "{\"status\":\"INACTIVE\"}", "customers:write").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange("/api/v1/customers", org.springframework.http.HttpMethod.GET, null, "customers:write")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange("/api/v1/customers", org.springframework.http.HttpMethod.POST, customerBody, "customers:read")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(customerPath, org.springframework.http.HttpMethod.GET, null, "customers:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(customerPath, org.springframework.http.HttpMethod.PUT, customerBody, "customers:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Reactivate the test customer through the scoped status endpoint for quote creation.
        assertThat(scopedExchange(customerPath + "/status", org.springframework.http.HttpMethod.PATCH,
                "{\"status\":\"ACTIVE\"}", "customers:write").getStatusCode()).isEqualTo(HttpStatus.OK);

        var product = scopedExchange("/api/v1/products", org.springframework.http.HttpMethod.POST,
                "{\"productCode\":\"AUTH-MATRIX\",\"name\":\"Authorization Matrix\",\"productType\":\"OTHER\"}",
                "products:write");
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String productPath = product.getHeaders().getLocation().getPath();
        UUID productId = UUID.fromString(productPath.substring(productPath.lastIndexOf('/') + 1));
        assertThat(scopedExchange("/api/v1/products", org.springframework.http.HttpMethod.GET, null, "products:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange("/api/v1/products/available", org.springframework.http.HttpMethod.GET, null, "products:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(productPath, org.springframework.http.HttpMethod.GET, null, "products:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(productPath, org.springframework.http.HttpMethod.PUT,
                "{\"name\":\"Authorization Matrix Updated\",\"productType\":\"OTHER\"}", "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        String versionBody = """
                {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":"2027-01-01T00:00:00Z","coverages":[
                  {"code":"TEST_COVER","displayName":"Test cover","coverageType":"OTHER","mandatory":true,"optional":false,"available":true}]}
                """;
        var version = scopedExchange(productPath + "/versions", org.springframework.http.HttpMethod.POST, versionBody, "products:write");
        assertThat(version.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String versionPath = version.getHeaders().getLocation().getPath();
        UUID versionId = UUID.fromString(versionPath.substring(versionPath.lastIndexOf('/') + 1));
        assertThat(scopedExchange(productPath + "/versions", org.springframework.http.HttpMethod.GET, null, "products:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(versionPath, org.springframework.http.HttpMethod.GET, null, "products:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(versionPath, org.springframework.http.HttpMethod.PUT,
                versionBody.replace("TEST_COVER", "TEST_COVER_UPDATED"), "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        String activatePath = versionPath + "/activate";
        assertThat(scopedExchange(activatePath, org.springframework.http.HttpMethod.POST, null, "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(productPath, org.springframework.http.HttpMethod.PUT,
                "{\"name\":\"Denied\",\"productType\":\"OTHER\"}", "products:read").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        var secondVersion = scopedExchange(productPath + "/versions", org.springframework.http.HttpMethod.POST,
                """
                {"effectiveFrom":"2027-01-01T00:00:00Z","effectiveTo":"2028-01-01T00:00:00Z","coverages":[
                  {"code":"TEST_COVER","displayName":"Test cover","coverageType":"OTHER","mandatory":true,"optional":false,"available":true}]}
                """, "products:write");
        assertThat(secondVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secondVersionPath = secondVersion.getHeaders().getLocation().getPath();
        UUID secondVersionId = UUID.fromString(secondVersionPath.substring(secondVersionPath.lastIndexOf('/') + 1));
        assertThat(scopedExchange(secondVersionPath + "/activate", org.springframework.http.HttpMethod.POST, null, "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        String quoteRequest = """
                {"customerId":"%s","productId":"%s","productVersionId":"%s","coverageEffectiveAt":"2027-06-01T00:00:00Z",
                 "vehicle":{"vehicleCategory":"CAR","manufacturer":"Test","model":"Model","manufactureYear":2020,"usageCategory":"PRIVATE"}}
                """.formatted(customerId, productId, secondVersionId);
        assertThat(scopedExchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, quoteRequest, "quotes:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, quoteRequest, "quotes:read")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var quote = scopedExchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, quoteRequest, "quotes:write");
        assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String quotePath = quote.getHeaders().getLocation().getPath();
        assertThat(scopedExchange(quotePath, org.springframework.http.HttpMethod.GET, null, "quotes:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange("/api/v1/quotes?customerId=" + customerId, org.springframework.http.HttpMethod.GET,
                null, "quotes:self").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(quotePath + "/submit", org.springframework.http.HttpMethod.POST, null, "quotes:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(quotePath + "/cancel", org.springframework.http.HttpMethod.POST, null, "quotes:self")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange("/api/v1/quotes", org.springframework.http.HttpMethod.GET, null, "quotes:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange("/api/v1/quotes?customerId=" + customerId, org.springframework.http.HttpMethod.GET, null, "quotes:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(quotePath, org.springframework.http.HttpMethod.GET, null, "quotes:read")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(quotePath + "/submit", org.springframework.http.HttpMethod.POST, null, "quotes:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(quotePath + "/cancel", org.springframework.http.HttpMethod.POST, null, "quotes:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(quotePath, org.springframework.http.HttpMethod.GET, null, "quotes:write")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(versionPath + "/deactivate", org.springframework.http.HttpMethod.POST, null, "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange(productPath + "/deactivate", org.springframework.http.HttpMethod.POST, null, "products:write")
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String path : java.util.List.of("/api/v1/health", "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            assertThat(scopedExchange(path, org.springframework.http.HttpMethod.GET, null, null).getStatusCode())
                    .as("public operational health route %s", path).isEqualTo(HttpStatus.OK);
        }
        assertThat(scopedExchange("/actuator/info", org.springframework.http.HttpMethod.GET, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(scopedExchange("/actuator/info", org.springframework.http.HttpMethod.GET, null, "core:ops").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(scopedExchange("/api/v1/not-a-route", org.springframework.http.HttpMethod.GET, null,
                "customers:read products:read quotes:read core:ops").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(productPath + "/unregistered-subroute", org.springframework.http.HttpMethod.GET, null,
                "products:read").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(quotePath + "/preview", org.springframework.http.HttpMethod.GET, null,
                "quotes:read").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange(customerPath + "/unregistered-subroute", org.springframework.http.HttpMethod.GET, null,
                "customers:read").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopedExchange("/unmatched", org.springframework.http.HttpMethod.GET, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(scopedExchange("/api/v1/customers", org.springframework.http.HttpMethod.GET, null,
                "customers:self").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var allowedCors = corsPreflight("https://app.example.test");
        assertThat(allowedCors.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowedCors.getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://app.example.test");
        var rejectedCors = corsPreflight("https://attacker.example");
        assertThat(rejectedCors.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejectedCors.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private org.springframework.http.ResponseEntity<String> corsPreflight(String origin) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(org.springframework.http.HttpMethod.POST);
        headers.set("Access-Control-Request-Headers", "authorization,content-type");
        return restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.OPTIONS,
                new org.springframework.http.HttpEntity<>(headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> scopedExchange(String path,
            org.springframework.http.HttpMethod method, String body, String scopes) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Correlation-ID", "authorization-matrix");
        if (body != null) headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (scopes != null) headers.setBearerAuth(bearerToken(scopes));
        else {
            var interceptors = restTemplate.getRestTemplate().getInterceptors();
            var automaticAuth = interceptors.stream().filter(i -> i instanceof CoreApiAuthInterceptor).findFirst().orElseThrow();
            interceptors.remove(automaticAuth);
            try {
                return restTemplate.exchange(path, method, new org.springframework.http.HttpEntity<>(body, headers), String.class);
            } finally {
                interceptors.add(automaticAuth);
            }
        }
        return restTemplate.exchange(path, method, new org.springframework.http.HttpEntity<>(body, headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> scopedExchangeAs(String path,
            org.springframework.http.HttpMethod method, String body, String scopes, String subject) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Correlation-ID", "identity-ownership-test");
        if (body != null) headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken(scopes, TestJwtConfiguration.AUDIENCE, TestJwtConfiguration.ISSUER,
                Instant.now().plusSeconds(600), subject));
        return restTemplate.exchange(path, method, new org.springframework.http.HttpEntity<>(body, headers), String.class);
    }

    private org.springframework.http.HttpHeaders headers(String scopes) {
        return headersWithAudience(scopes, TestJwtConfiguration.AUDIENCE);
    }

    private org.springframework.http.HttpHeaders headersWithAudience(String scopes, String audience) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(bearerToken(scopes, audience));
        headers.set("X-Correlation-ID", "security-test-1");
        return headers;
    }

    private String bearerToken(String scopes) {
        return bearerToken(scopes, TestJwtConfiguration.AUDIENCE);
    }

    private String bearerToken(String scopes, String audience) {
        return bearerToken(scopes, audience, TestJwtConfiguration.ISSUER, Instant.now().plusSeconds(600));
    }

    private org.springframework.http.HttpHeaders headersFor(String scopes, String issuer, Instant expiresAt) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(bearerToken(scopes, TestJwtConfiguration.AUDIENCE, issuer, expiresAt));
        return headers;
    }

    private org.springframework.http.HttpHeaders headersNotBefore(String scopes, Instant notBefore) {
        var claims = JwtClaimsSet.builder().issuer(TestJwtConfiguration.ISSUER).subject("operations-test-user")
                .audience(java.util.List.of(TestJwtConfiguration.AUDIENCE)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).notBefore(notBefore).claim("scope", scopes).build();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue());
        return headers;
    }

    private String bearerToken(String scopes, String audience, String issuer, Instant expiresAt) {
        return bearerToken(scopes, audience, issuer, expiresAt, "operations-test-user");
    }

    private String bearerToken(String scopes, String audience, String issuer, Instant expiresAt, String subject) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .audience(java.util.List.of(audience)).issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt)
                .claim("scope", scopes).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private String bearerTokenWithScpOnly(String scope) {
        var claims = JwtClaimsSet.builder().issuer(TestJwtConfiguration.ISSUER).subject("scp-only-test-user")
                .audience(java.util.List.of(TestJwtConfiguration.AUDIENCE)).issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().plusSeconds(600)).claim("scp", scope).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private String corruptSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static final class CoreApiAuthInterceptor implements ClientHttpRequestInterceptor {
        private final String token;
        private CoreApiAuthInterceptor(String token) { this.token = token; }
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request, byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {
            if (request.getHeaders().getFirst("Authorization") == null) {
                request.getHeaders().setBearerAuth(token);
            }
            return execution.execute(request, body);
        }
    }

    @Test
    void startsAgainstPostgresAndServesHealth() {
        var response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"service\":\"core-api\"");
        var readiness = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"");
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class)).isEqualTo("motor_core");
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo("motorsure");
        assertThat(flyway.info().applied()).hasSize(8);
        assertThat(jdbcTemplate.queryForObject(
                "select to_regclass('public.flyway_schema_history') is not null", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('customers', 'products', 'product_versions', 'product_version_coverages',
                                     'quotes', 'quote_vehicle_risk', 'quote_coverage_snapshots',
                                     'customer_identity_links')
                """, Long.class)).isEqualTo(8L);
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

    @Test
    void quoteLifecyclePersistsRiskAndImmutableCoverageSnapshotWithoutPrice() {
        var customer = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST, json("""
                {"firstName":"Nina","lastName":"Test","email":"nina.quote@example.test","dateOfBirth":"1990-01-01"}
                """), String.class);
        UUID customerId = UUID.fromString(customer.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var product = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST, json("""
                {"productCode":"QUOTE-CAR","name":"Quote Motor","productType":"COMPREHENSIVE"}
                """), String.class);
        UUID productId = UUID.fromString(product.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var version = restTemplate.exchange("/api/v1/products/" + productId + "/versions", org.springframework.http.HttpMethod.POST, json("""
                {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":"2027-01-01T00:00:00Z","coverages":[
                  {"code":"OWN_DAMAGE","displayName":"Own damage","description":"Synthetic test coverage","coverageType":"OWN_DAMAGE","mandatory":false,"optional":true,"available":true}]}
                """), String.class);
        UUID versionId = UUID.fromString(version.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        assertThat(restTemplate.postForEntity("/api/v1/products/" + productId + "/versions/" + versionId + "/activate", null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/api/v1/products/" + productId, String.class).getBody()).contains("\"status\":\"ACTIVE\"");
        assertThat(restTemplate.getForEntity("/api/v1/products/" + productId + "/versions/" + versionId, String.class).getBody())
                .contains("\"status\":\"ACTIVE\"").contains("2026-01-01T00:00:00Z");

        String request = """
                {"customerId":"%s","productId":"%s","productVersionId":"%s","coverageEffectiveAt":"2026-01-01T00:00:00Z","vehicle":{
                 "vehicleCategory":"CAR","manufacturer":" Example Motors ","model":"Model A","manufactureYear":2020,
                 "registrationNumber":"TEST-123","fuelType":"HYBRID","usageCategory":"PRIVATE",
                 "declaredVehicleValue":12500.50,"declaredVehicleCurrency":"USD"}}
                """.formatted(customerId, productId, versionId);
        var created = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, json(request), String.class);
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
        assertThat(created.getBody()).contains("\"status\":\"DRAFT\"")
                .contains("\"pricingStatus\":\"NOT_CALCULATED\"")
                .contains("\"expiresAt\":null")
                .contains("\"coverageCode\":\"OWN_DAMAGE\"")
                .contains("\"manufacturer\":\"Example Motors\"")
                .doesNotContain("premium");
        UUID quoteId = UUID.fromString(created.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var unsupportedUsdPrecision = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace("12500.50", "12500.501")), String.class);
        assertThat(unsupportedUsdPrecision.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var secondQuote = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace("12500.50", "12500.125").replace("USD", "KWD")
                        .replace("KWD", "kwd")
                        .replace("\"coverageEffectiveAt\":\"2026-01-01T00:00:00Z\",", "")), String.class);
        assertThat(secondQuote.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondQuote.getBody()).contains("\"pricingStatus\":\"NOT_CALCULATED\"")
                .contains("\"coverageEffectiveAt\":\"2026-01-01T00:00:00Z\"")
                .contains("\"declaredVehicleCurrency\":\"KWD\"");
        assertThat(secondQuote.getBody()).doesNotContain(extractQuoteNumber(created.getBody()));
        assertThat(jdbcTemplate.queryForObject("select count(*) from quotes where id = ? and pricing_status = 'NOT_CALCULATED'", Long.class, quoteId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from quote_coverage_snapshots where quote_id = ?", Long.class, quoteId)).isEqualTo(1L);
        Throwable snapshotUpdateFailure = org.assertj.core.api.Assertions.catchThrowable(() -> jdbcTemplate.update(
                "update quote_coverage_snapshots set display_name = 'Tampered' where quote_id = ?", quoteId));
        assertThat(snapshotUpdateFailure).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sqlStateOf(snapshotUpdateFailure)).isEqualTo("55000");
        Throwable snapshotDeleteFailure = org.assertj.core.api.Assertions.catchThrowable(() -> jdbcTemplate.update(
                "delete from quote_coverage_snapshots where quote_id = ?", quoteId));
        assertThat(snapshotDeleteFailure).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sqlStateOf(snapshotDeleteFailure)).isEqualTo("55000");
        assertThat(jdbcTemplate.queryForObject("select display_name from quote_coverage_snapshots where quote_id = ?",
                String.class, quoteId)).isEqualTo("Own damage");
        Throwable quoteDeleteFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> jdbcTemplate.update("delete from quotes where id = ?", quoteId));
        assertThat(quoteDeleteFailure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlStateOf(quoteDeleteFailure)).isEqualTo("23503");
        assertThat(jdbcTemplate.queryForObject("select count(*) from quotes where id = ?", Long.class, quoteId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from quote_vehicle_risk where quote_id = ?", Long.class, quoteId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from quote_coverage_snapshots where quote_id = ?", Long.class, quoteId)).isEqualTo(1L);

        var endBoundary = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")), String.class);
        assertThat(endBoundary.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(endBoundary.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
        var otherProduct = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST,
                json("{\"productCode\":\"OTHER-CATALOG\",\"name\":\"Other Catalog\",\"productType\":\"OTHER\"}"), String.class);
        UUID otherProductId = UUID.fromString(otherProduct.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into quotes (id, quote_number, customer_id, product_id, product_version_id,
                                    coverage_effective_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now(), now())
                """, UUID.randomUUID(), "QTE-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                customerId, otherProductId, versionId)).isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("delete from customers where id = ?", customerId))
                .isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("delete from product_versions where id = ?", versionId))
                .isInstanceOf(DataIntegrityViolationException.class);
        var mismatchedVersion = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace(productId.toString(), otherProductId.toString())), String.class);
        assertThat(mismatchedVersion.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var missingProduct = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace(productId.toString(), "00000000-0000-0000-0000-000000000004")), String.class);
        assertThat(missingProduct.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Deactivation changes catalog lifecycle, but leaves the quote's captured snapshot intact.
        restTemplate.postForEntity("/api/v1/products/" + productId + "/versions/" + versionId + "/deactivate", null, String.class);
        var fetched = restTemplate.getForEntity("/api/v1/quotes/" + quoteId, String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).contains("Synthetic test coverage").contains("OWN_DAMAGE");
        var inactiveVersion = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request), String.class);
        assertThat(inactiveVersion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from quotes", Long.class)).isEqualTo(2L);

        var page = restTemplate.getForEntity("/api/v1/quotes?customerId=" + customerId + "&page=0&size=10", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("\"totalElements\":2");
        Quote staleQuote = quoteRepository.findById(quoteId).orElseThrow();
        var submitted = restTemplate.postForEntity("/api/v1/quotes/" + quoteId + "/submit", null, String.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK).as(submitted.getBody());
        assertThat(submitted.getBody()).contains("PENDING_UNDERWRITING");
        staleQuote.cancel(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quoteRepository.saveAndFlush(staleQuote))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        var submittedAgain = restTemplate.postForEntity("/api/v1/quotes/" + quoteId + "/submit", null, String.class);
        assertThat(submittedAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var cancelled = restTemplate.postForEntity("/api/v1/quotes/" + quoteId + "/cancel", null, String.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).contains("CANCELLED");
        var cancelAgain = restTemplate.postForEntity("/api/v1/quotes/" + quoteId + "/cancel", null, String.class);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void quoteRejectsUnknownReferencesInvalidDatesAndValueCurrencyMismatchSafely() {
        var unknownCustomer = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json("""
                        {"customerId":"00000000-0000-0000-0000-000000000001","productId":"00000000-0000-0000-0000-000000000002",
                         "productVersionId":"00000000-0000-0000-0000-000000000003","vehicle":{"vehicleCategory":"CAR",
                         "manufacturer":"Test","model":"A","manufactureYear":2020,"usageCategory":"PRIVATE"}}
                        """), String.class);
        assertThat(unknownCustomer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var invalid = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, json("""
                {"customerId":"00000000-0000-0000-0000-000000000001","productId":"00000000-0000-0000-0000-000000000002",
                 "productVersionId":"00000000-0000-0000-0000-000000000003","vehicle":{"vehicleCategory":"CAR",
                 "manufacturer":" ","model":"A","manufactureYear":2020,"usageCategory":"PRIVATE","declaredVehicleValue":100}}
                """), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).contains("request.validation_failed").doesNotContain("00000000-0000-0000-0000-000000000001");
    }

    @RepeatedTest(3)
    void quoteCreationAndProductDeactivationSerializeAcrossIndependentTransactions() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setTimeout(15);
        ExecutorService contenders = Executors.newFixedThreadPool(2);
        CountDownLatch quoteCreatedInsideTransaction = new CountDownLatch(1);
        CountDownLatch allowQuoteCommit = new CountDownLatch(1);
        CountDownLatch allowDeactivationCommit = new CountDownLatch(1);
        try {
            ProductFixture quoteFirst = createActiveProductFixture();
            Future<com.motorsure.coreapi.quote.api.QuoteResponses.View> quoteTx = contenders.submit(() ->
                    transactions.execute(status -> {
                        var quote = quoteService.create(quoteFirst.command());
                        quoteCreatedInsideTransaction.countDown();
                        awaitLatch(allowQuoteCommit, "quote transaction was not released");
                        return quote;
                    }));
            assertThat(quoteCreatedInsideTransaction.await(10, TimeUnit.SECONDS))
                    .as("quote must create its complete aggregate before holding the Product lock")
                    .isTrue();

            Future<org.springframework.http.ResponseEntity<String>> deactivateAfterQuote = contenders.submit(() ->
                    restTemplate.postForEntity("/api/v1/products/" + quoteFirst.productId() + "/versions/"
                            + quoteFirst.versionId() + "/deactivate", null, String.class));
            await().alias("deactivation waiting for the quote transaction's Product lock")
                    .pollDelay(java.time.Duration.ZERO).pollInterval(java.time.Duration.ofMillis(20))
                    .atMost(java.time.Duration.ofSeconds(8))
                    .untilAsserted(() -> assertThat(postgresLockWaiters()).isEqualTo(1L));
            allowQuoteCommit.countDown();

            var committedQuote = quoteTx.get(12, TimeUnit.SECONDS);
            var deactivated = deactivateAfterQuote.get(12, TimeUnit.SECONDS);
            assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbcTemplate.queryForObject("select count(*) from quotes where id = ?", Long.class,
                    committedQuote.id())).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("select count(*) from quote_vehicle_risk where quote_id = ?",
                    Long.class, committedQuote.id())).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("select count(*) from quote_coverage_snapshots where quote_id = ?",
                    Long.class, committedQuote.id())).isEqualTo(1L);
            assertThat(committedQuote.coverageSnapshot()).hasSize(1);

            ProductFixture deactivateFirst = createActiveProductFixture();
            CountDownLatch deactivationAppliedInsideTransaction = new CountDownLatch(1);
            Future<ProductVersion> deactivationTx = contenders.submit(() -> transactions.execute(status -> {
                ProductVersion version = productService.deactivateVersion(deactivateFirst.productId(), deactivateFirst.versionId());
                deactivationAppliedInsideTransaction.countDown();
                awaitLatch(allowDeactivationCommit, "deactivation transaction was not released");
                return version;
            }));
            assertThat(deactivationAppliedInsideTransaction.await(10, TimeUnit.SECONDS))
                    .as("deactivation must hold the Product lock before quote creation starts")
                    .isTrue();

            long quotesBeforeRejectedCreation = jdbcTemplate.queryForObject("select count(*) from quotes", Long.class);
            Future<org.springframework.http.ResponseEntity<String>> rejectedQuote = contenders.submit(() ->
                    restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                            json(deactivateFirst.requestJson()), String.class));
            await().alias("quote creation waiting for the deactivation transaction's Product lock")
                    .pollDelay(java.time.Duration.ZERO).pollInterval(java.time.Duration.ofMillis(20))
                    .atMost(java.time.Duration.ofSeconds(8))
                    .untilAsserted(() -> assertThat(postgresLockWaiters()).isEqualTo(1L));
            allowDeactivationCommit.countDown();

            deactivationTx.get(12, TimeUnit.SECONDS);
            var conflict = rejectedQuote.get(12, TimeUnit.SECONDS);
            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT).as(conflict.getBody());
            assertThat(jdbcTemplate.queryForObject("select count(*) from quotes", Long.class))
                    .isEqualTo(quotesBeforeRejectedCreation);
            assertThat(jdbcTemplate.queryForObject("select count(*) from quote_coverage_snapshots", Long.class))
                    .isEqualTo(1L);
        } finally {
            allowQuoteCommit.countDown();
            allowDeactivationCommit.countDown();
            contenders.shutdownNow();
            assertThat(contenders.awaitTermination(10, TimeUnit.SECONDS))
                    .as("concurrent transaction workers must terminate")
                    .isTrue();
        }
    }

    @Test
    void quoteAcceptsUnboundedActiveVersionAndDefaultsCoverageInstantFromClock() {
        var customer = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST, json("""
                {"firstName":"Omar","lastName":"Test","email":"omar.quote@example.test","dateOfBirth":"1990-01-01"}
                """), String.class);
        UUID customerId = UUID.fromString(customer.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var product = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST, json("""
                {"productCode":"OPEN-ENDED","name":"Open Ended","productType":"OTHER"}
                """), String.class);
        UUID productId = UUID.fromString(product.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var version = restTemplate.exchange("/api/v1/products/" + productId + "/versions", org.springframework.http.HttpMethod.POST, json("""
                {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":null,"coverages":[
                  {"code":"OTHER_COVER","displayName":"Other cover","coverageType":"OTHER","mandatory":true,"optional":false,"available":true}]}
                """), String.class);
        UUID versionId = UUID.fromString(version.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        assertThat(restTemplate.postForEntity("/api/v1/products/" + productId + "/versions/" + versionId + "/activate", null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        String request = """
                {"customerId":"%s","productId":"%s","productVersionId":"%s","vehicle":{
                 "vehicleCategory":"OTHER","manufacturer":"Example","model":"Vehicle","manufactureYear":2020,
                 "usageCategory":"OTHER"}}
                """.formatted(customerId, productId, versionId);
        var futureYear = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST,
                json(request.replace("\"manufactureYear\":2020", "\"manufactureYear\":2027")), String.class);
        assertThat(futureYear.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("select count(*) from quotes", Long.class)).isZero();

        var created = restTemplate.exchange("/api/v1/quotes", org.springframework.http.HttpMethod.POST, json(request), String.class);
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"coverageEffectiveAt\":\"2026-01-01T00:00:00Z\"")
                .contains("\"coverageCode\":\"OTHER_COVER\"");
    }

    private ProductFixture createActiveProductFixture() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        var customer = restTemplate.exchange("/api/v1/customers", org.springframework.http.HttpMethod.POST,
                json("""
                        {"firstName":"Race","lastName":"Test","email":"race-%s@example.test","dateOfBirth":"1990-01-01"}
                        """.formatted(marker)), String.class);
        assertThat(customer.getStatusCode()).as(customer.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID customerId = UUID.fromString(customer.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));

        var product = restTemplate.exchange("/api/v1/products", org.springframework.http.HttpMethod.POST,
                json("""
                        {"productCode":"RACE-%s","name":"Race Product","productType":"COMPREHENSIVE"}
                        """.formatted(marker)), String.class);
        assertThat(product.getStatusCode()).as(product.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID productId = UUID.fromString(product.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));

        var version = restTemplate.exchange("/api/v1/products/" + productId + "/versions",
                org.springframework.http.HttpMethod.POST, json("""
                        {"effectiveFrom":"2026-01-01T00:00:00Z","effectiveTo":"2027-01-01T00:00:00Z","coverages":[
                          {"code":"LIABILITY","displayName":"Third party liability","coverageType":"THIRD_PARTY_LIABILITY",
                           "mandatory":true,"optional":false,"available":true}]}
                        """), String.class);
        assertThat(version.getStatusCode()).as(version.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID versionId = UUID.fromString(version.getHeaders().getLocation().getPath().replaceFirst(".*/", ""));
        var activated = restTemplate.postForEntity("/api/v1/products/" + productId + "/versions/" + versionId + "/activate",
                null, String.class);
        assertThat(activated.getStatusCode()).as(activated.getBody()).isEqualTo(HttpStatus.OK);

        String requestJson = """
                {"customerId":"%s","productId":"%s","productVersionId":"%s","coverageEffectiveAt":"2026-06-01T00:00:00Z",
                 "vehicle":{"vehicleCategory":"CAR","manufacturer":"Test Motors","model":"Race","manufactureYear":2020,
                 "registrationNumber":"RACE-01","fuelType":"PETROL","usageCategory":"PRIVATE",
                 "declaredVehicleValue":10000.00,"declaredVehicleCurrency":"USD"}}
                """.formatted(customerId, productId, versionId);
        var command = new QuoteRequests.Create(customerId, productId, versionId,
                Instant.parse("2026-06-01T00:00:00Z"), new QuoteRequests.Vehicle(VehicleCategory.CAR,
                "Test Motors", "Race", 2020, "RACE-01", FuelType.PETROL, UsageCategory.PRIVATE,
                new BigDecimal("10000.00"), "USD"));
        return new ProductFixture(productId, versionId, requestJson, command);
    }

    private long postgresLockWaiters() {
        return jdbcTemplate.queryForObject("""
                select count(*) from pg_stat_activity
                where datname = current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock'
                """, Long.class);
    }

    private static String sqlStateOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException) return sqlException.getSQLState();
        }
        return null;
    }

    private static void awaitLatch(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError(failureMessage);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, interrupted);
        }
    }

    private record ProductFixture(UUID productId, UUID versionId, String requestJson, QuoteRequests.Create command) { }

    private org.springframework.http.HttpEntity<String> json(String body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }

    private String extractQuoteNumber(String body) {
        var matcher = java.util.regex.Pattern.compile("\\\"quoteNumber\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
