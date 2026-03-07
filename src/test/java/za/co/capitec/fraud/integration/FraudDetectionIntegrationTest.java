package za.co.capitec.fraud.integration;

import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.dto.TransactionRequest;
import za.co.capitec.fraud.dto.TransactionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FraudDetectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDetectHighValueFraud() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TXN_HIGH_001")
                .customerId("CUST_TEST_001")
                .amount(new BigDecimal("75000.00"))
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Suspicious Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TransactionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransactionResponse.class
        );

        assertTrue(response.alertCount() > 0);
        assertNotEquals("APPROVED", response.status().toString());
    }

    @Test
    void shouldApproveNormalTransaction() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TXN_NORMAL_001")
                .customerId("CUST_TEST_002")
                .amount(new BigDecimal("150.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Local Grocery Store")
                .timestamp(LocalDateTime.now())
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TransactionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransactionResponse.class
        );

        assertEquals(0, response.alertCount());
        assertEquals("APPROVED", response.status().toString());
    }

    @Test
    void shouldRetrieveAlertsForCustomer() throws Exception {
        // First, create a flagged transaction
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TXN_ALERT_001")
                .customerId("CUST_ALERT_TEST")
                .amount(new BigDecimal("60000.00"))
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then retrieve alerts
        mockMvc.perform(get("/api/v1/fraud-alerts/customer/CUST_ALERT_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}