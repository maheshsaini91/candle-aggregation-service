package com.multibank.aggregationservice;

import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.services.CandleAggregationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "candle.generator.enabled=false",
        "server.servlet.context-path=",
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics",
        "management.metrics.export.prometheus.enabled=true"
})
@AutoConfigureMockMvc
class HistoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired
    CandleAggregationService service;

    private static final long   BASE_TS = 1620000000L;
    private static final String SYMBOL  = "BTC-USD";

    @Test
    @DisplayName("GET /v1/history returns 200 with correct TradingView UDF structure")
    void returnsOkWithData() throws Exception {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 60));

        mockMvc.perform(get("/v1/history")
                        .param("symbol", SYMBOL)
                        .param("interval", "1m")
                        .param("from", String.valueOf(BASE_TS))
                        .param("to", String.valueOf(BASE_TS + 59)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("ok"))
                .andExpect(jsonPath("$.t", hasSize(1)))
                .andExpect(jsonPath("$.t[0]").value(BASE_TS))
                .andExpect(jsonPath("$.o[0]").value(29505.0))
                .andExpect(jsonPath("$.v[0]").value(1));
    }

    @Test
    @DisplayName("GET /v1/history returns no_data for unknown symbol")
    void returnsNoData() throws Exception {
        mockMvc.perform(get("/v1/history")
                        .param("symbol", "UNKNOWN")
                        .param("interval", "1m")
                        .param("from", String.valueOf(BASE_TS))
                        .param("to", String.valueOf(BASE_TS + 60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("no_data"))
                .andExpect(jsonPath("$.t", hasSize(0)));
    }

    @Test
    @DisplayName("Returns 400 when from > to")
    void returns400WhenFromAfterTo() throws Exception {
        mockMvc.perform(get("/v1/history")
                        .param("symbol", SYMBOL).param("interval", "1m")
                        .param("from", String.valueOf(BASE_TS + 60))
                        .param("to", String.valueOf(BASE_TS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 400 for unsupported interval")
    void returns400ForBadInterval() throws Exception {
        mockMvc.perform(get("/v1/history")
                        .param("symbol", SYMBOL).param("interval", "2m")
                        .param("from", String.valueOf(BASE_TS))
                        .param("to", String.valueOf(BASE_TS + 120)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 400 when range exceeds 7-day maximum")
    void returns400WhenRangeTooLarge() throws Exception {
        long sevenDaysPlus = 7 * 24 * 3600L + 1;
        mockMvc.perform(get("/v1/history")
                        .param("symbol", SYMBOL).param("interval", "1m")
                        .param("from", String.valueOf(BASE_TS))
                        .param("to", String.valueOf(BASE_TS + sevenDaysPlus)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 400 when symbol is blank")
    void returns400WhenSymbolBlank() throws Exception {
        mockMvc.perform(get("/v1/history")
                        .param("symbol", "").param("interval", "1m")
                        .param("from", String.valueOf(BASE_TS))
                        .param("to", String.valueOf(BASE_TS + 60)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /actuator/health returns UP")
    void healthCheckUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/metrics exposes candle metrics")
    void metricsEndpointExposesCandleMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("candle")));
    }
}
