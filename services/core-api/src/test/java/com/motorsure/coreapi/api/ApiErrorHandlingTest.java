package com.motorsure.coreapi.api;

import com.motorsure.coreapi.common.web.ApiExceptionHandler;
import com.motorsure.coreapi.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, ErrorHandlingTestController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, ApiErrorHandlingSecurityConfiguration.class})
class ApiErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validatesAndPropagatesIncomingCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(CorrelationIdFilter.HEADER_NAME, "web-client.42_trace"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "web-client.42_trace"));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesMalformedCorrelationIdAndClearsRequestContext() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdFilter.HEADER_NAME, "unsafe id\nvalue"))
                .andExpect(status().isOk())
                .andReturn();

        String generatedId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(UUID.fromString(generatedId)).isNotNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mapsValidationFailureToSafeProblemDetailWithoutRejectedValue() throws Exception {
        String correlationId = "test-validation-1";
        String sensitiveValue = "policyholder-sensitive-value";

        mockMvc.perform(post("/test-only/validation")
                        .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + sensitiveValue + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:motorsure:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more request fields are invalid."))
                .andExpect(jsonPath("$.instance").value("/test-only/validation"))
                .andExpect(jsonPath("$.code").value("request.validation_failed"))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.invalidParams[0].name").value("value"))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("must be at most 8 characters"))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, correlationId))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(sensitiveValue))));
    }

    @Test
    void mapsMalformedJsonToClientError() throws Exception {
        mockMvc.perform(post("/test-only/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.malformed"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or could not be parsed."));
    }

    @Test
    void mapsMissingResourceWithoutExposingFrameworkReason() throws Exception {
        mockMvc.perform(get("/test-only/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource.not_found"))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Sensitive"))));
    }

    @Test
    void mapsConflictToConflictStatus() throws Exception {
        mockMvc.perform(get("/test-only/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.conflict"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with the current resource state."));
    }

    @Test
    void hidesUnexpectedServerErrorDetailsFromClient() throws Exception {
        mockMvc.perform(get("/test-only/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.detail").value("The server could not complete the request."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Sensitive persistence detail"))));
    }
}
