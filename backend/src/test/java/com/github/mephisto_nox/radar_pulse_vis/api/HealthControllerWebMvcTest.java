package com.github.mephisto_nox.radar_pulse_vis.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.kafka.admin.auto-create=false")
@AutoConfigureMockMvc
class HealthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsServiceAsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("pulse-vision-api"))
                .andExpect(jsonPath("$.packetPipeline").value("UP"));
    }

    @Test
    void pdwHealthEndpointReportsReceiverAndMetrics() throws Exception {
        mockMvc.perform(get("/api/pdw/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.udpReceiver").value("UP"))
                .andExpect(jsonPath("$.kafkaPublisherWorker").value("UP"))
                .andExpect(jsonPath("$.metrics.receivedDatagrams").value(0));
    }
}