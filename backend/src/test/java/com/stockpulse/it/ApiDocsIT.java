package com.stockpulse.it;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger UI serves the hand-written contract, not code-generated docs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class ApiDocsIT {
  @Autowired MockMvc mvc;

  @Test
  void contractIsPackagedAndServed() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("title: StockPulse API")))
        .andExpect(content().string(containsString("operationId: createOrder")));
  }

  @Test
  void swaggerUiPointsAtTheContract() throws Exception {
    mvc.perform(get("/swagger-ui.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui/index.html"));
    mvc.perform(get("/v3/api-docs-generated/swagger-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/openapi.yaml"));
  }
}
