package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.engine.FraudRuleResult;
import za.co.capitec.fraud.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RapidLocationChangeRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private ObjectMapper objectMapper;
    private RapidLocationChangeRule rule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new RapidLocationChangeRule(transactionRepository, objectMapper);
    }

    @Test
    void shouldFlagHighRiskLocation() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("5000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)
                .merchantName("Suspicious Merchant")
                .location("TOR Exit Node, Unknown")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());
        assertEquals("RAPID_LOCATION_CHANGE", result.ruleName());
        assertEquals(FraudSeverity.HIGH, result.severity());
        assertTrue(result.reason().contains("high-risk location"));
    }

    @Test
    void shouldFlagRapidLocationChange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusMinutes(60);

        // Previous transaction in Johannesburg
        Transaction previousTransaction = Transaction.builder()
                .transactionId("TXN-PREV")
                .customerId("CUST002")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Restaurant JHB")
                .location("Johannesburg, ZA")
                .timestamp(oneHourAgo)
                .build();

        // Current transaction in Cape Town (1400km away, 60 minutes later - impossible)
        Transaction currentTransaction = Transaction.builder()
                .transactionId("TXN002")
                .customerId("CUST002")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Restaurant CPT")
                .location("Cape Town, ZA")
                .timestamp(now)
                .build();

        when(transactionRepository.findByCustomerIdAndTimestampBetween(
                eq("CUST002"), any(), any()))
                .thenReturn(List.of(previousTransaction));

        FraudRuleResult result = rule.evaluate(currentTransaction);

        assertTrue(result.flagged());
        assertEquals("RAPID_LOCATION_CHANGE", result.ruleName());
        assertTrue(result.reason().contains("Rapid location change"));
        assertTrue(result.reason().contains("Johannesburg"));
        assertTrue(result.reason().contains("Cape Town"));
    }

    @Test
    void shouldNotFlagWhenNoLocationProvided() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN003")
                .customerId("CUST003")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.P2P_PAYMENT)
                .merchantName("Mobile Payment")
                .location(null)  // No location
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());
    }

    @Test
    void shouldNotFlagWhenSameLocation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyMinutesAgo = now.minusMinutes(30);

        Transaction previousTransaction = Transaction.builder()
                .transactionId("TXN-PREV")
                .customerId("CUST004")
                .amount(new BigDecimal("500.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Store A")
                .location("Sandton, Johannesburg, ZA")
                .timestamp(thirtyMinutesAgo)
                .build();

        Transaction currentTransaction = Transaction.builder()
                .transactionId("TXN004")
                .customerId("CUST004")
                .amount(new BigDecimal("500.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Store B")
                .location("Sandton, Johannesburg, ZA")  // Same location
                .timestamp(now)
                .build();

        when(transactionRepository.findByCustomerIdAndTimestampBetween(
                eq("CUST004"), any(), any()))
                .thenReturn(List.of(previousTransaction));

        FraudRuleResult result = rule.evaluate(currentTransaction);

        assertFalse(result.flagged());
    }
}