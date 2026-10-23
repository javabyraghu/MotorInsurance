package com.motorsure.coreapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.motorsure.coreapi.security.SecurityConfiguration;
import com.motorsure.coreapi.security.SecurityProblemWriter;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(HealthController.class)
@Import({SecurityConfiguration.class, SecurityProblemWriter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsApiHealth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"UP","service":"core-api"}
                        """));
    }

    @Test
    void missingJwtConfigurationKeepsBusinessRoutesClosed() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("security.authentication_required"));

        // With no configured decoder, even a bearer-shaped string must not be accepted.
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0."))
                .andExpect(status().isUnauthorized());
    }
}
