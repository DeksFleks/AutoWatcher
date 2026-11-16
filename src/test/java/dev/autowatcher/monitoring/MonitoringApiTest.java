package dev.autowatcher.monitoring;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class MonitoringApiTest {
    @Autowired MockMvc mvc;

    @Test void absentProfileIsProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/profiles/" + UUID.randomUUID() + "/checks"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(404));
    }
    @Test void malformedJsonAndNullCriteriaAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/profiles").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/v1/profiles").contentType("application/json")
                .content("{\"name\":\"demo\",\"sources\":[\"FAKE\"]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
    @Test void missingStatusMustNotSilentlyDisableProfile() throws Exception {
        mvc.perform(patch("/api/v1/profiles/" + UUID.randomUUID() + "/status")
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
    @Test void invalidIdIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/profiles/not-a-uuid/checks"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
