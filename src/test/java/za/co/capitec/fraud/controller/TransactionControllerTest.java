package za.co.capitec.fraud.controller;

import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.domain.TransactionStatus;
import za.co.capitec.fraud.dto.TransactionRequest;
import za.co.capitec.fraud.dto.TransactionResponse;
import za.co.capitec.fraud.service.FraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FraudDetectionService fraudDetectionService;

    @Test
    void shouldProcessValidTransaction() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Test Merchant")
                .build();

        var response = new TransactionResponse(
                "TXN001",
                "CUST001",
                TransactionType.CARD_PURCHASE_POS,
                TransactionStatus.APPROVED,
                0,
                Collections.emptyList(),
                "Transaction approved - no fraud detected"
        );

        when(fraudDetectionService.processTransaction(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN001"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void shouldRejectInvalidTransaction() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("")
                .customerId("CUST001")
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}