package za.co.capitec.fraud.controller;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.dto.FraudAlertResponse;
import za.co.capitec.fraud.service.FraudDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FraudAlertController.class)
class FraudAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudDetectionService fraudDetectionService;

    @Test
    void shouldGetAlertsByCustomer() throws Exception {
        String customerId = "CUST001";
        FraudAlertResponse alert = new FraudAlertResponse(
                1L,
                "TXN001",
                customerId,
                TransactionType.EFT_TRANSFER,
                "HIGH_VALUE_TRANSACTION",
                FraudSeverity.HIGH,
                "Transaction amount exceeds threshold",
                75,
                "{\"amount\":\"60000.00\",\"threshold\":\"50000.00\"}",
                LocalDateTime.now()
        );

        when(fraudDetectionService.getAlertsByCustomer(customerId))
                .thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/fraud-alerts/customer/{customerId}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].customerId").value(customerId))
                .andExpect(jsonPath("$[0].ruleName").value("HIGH_VALUE_TRANSACTION"));
    }

    @Test
    void shouldRejectInvalidCustomerId() throws Exception {
        // Test with customerId that exceeds max length (> 255 characters)
        String tooLongCustomerId = "A".repeat(300);
        mockMvc.perform(get("/api/v1/fraud-alerts/customer/{customerId}", tooLongCustomerId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetRecentAlerts() throws Exception {
        int hours = 24;
        FraudAlertResponse alert = new FraudAlertResponse(
                1L,
                "TXN001",
                "CUST001",
                TransactionType.EFT_TRANSFER,
                "VELOCITY",
                FraudSeverity.CRITICAL,
                "Too many transactions",
                90,
                "{\"count\":\"15\",\"maxAllowed\":\"10\"}",
                LocalDateTime.now()
        );

        when(fraudDetectionService.getRecentAlerts(hours))
                .thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/fraud-alerts/recent")
                        .param("hours", String.valueOf(hours)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ruleName").value("VELOCITY"));
    }

    @Test
    void shouldRejectInvalidHoursParameter() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-alerts/recent")
                        .param("hours", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetAlertsByCustomerPaginated() throws Exception {
        String customerId = "CUST001";
        FraudAlertResponse alert = new FraudAlertResponse(
                1L,
                "TXN001",
                customerId,
                TransactionType.EFT_TRANSFER,
                "HIGH_VALUE_TRANSACTION",
                FraudSeverity.HIGH,
                "Transaction amount exceeds threshold",
                75,
                "{\"amount\":\"60000.00\"}",
                LocalDateTime.now()
        );

        Page<FraudAlertResponse> page = new PageImpl<>(
                List.of(alert),
                PageRequest.of(0, 20),
                1
        );

        when(fraudDetectionService.getAlertsByCustomerPaginated(eq(customerId), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/fraud-alerts/customer/{customerId}/paginated", customerId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].customerId").value(customerId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldGetRecentAlertsPaginated() throws Exception {
        int hours = 48;
        FraudAlertResponse alert = new FraudAlertResponse(
                2L,
                "TXN002",
                "CUST002",
                TransactionType.CARD_PURCHASE_ONLINE,
                "UNUSUAL_TIME",
                FraudSeverity.MEDIUM,
                "Transaction at unusual hour",
                50,
                "{\"hour\":\"3\",\"amount\":\"15000.00\"}",
                LocalDateTime.now()
        );

        Page<FraudAlertResponse> page = new PageImpl<>(
                List.of(alert),
                PageRequest.of(0, 20),
                1
        );

        when(fraudDetectionService.getRecentAlertsPaginated(eq(hours), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/fraud-alerts/recent/paginated")
                        .param("hours", String.valueOf(hours))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].ruleName").value("UNUSUAL_TIME"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnEmptyListWhenNoAlertsFound() throws Exception {
        String customerId = "CUST_NO_ALERTS";

        when(fraudDetectionService.getAlertsByCustomer(customerId))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/fraud-alerts/customer/{customerId}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
