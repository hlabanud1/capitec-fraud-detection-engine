package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionStatus;
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
class RepeatedDeclinedTransactionRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private ObjectMapper objectMapper;
    private RepeatedDeclinedTransactionRule rule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new RepeatedDeclinedTransactionRule(transactionRepository, objectMapper);
    }

    @Test
    void shouldFlagWhenMaxBlockedAttemptsReached() {
        LocalDateTime now = LocalDateTime.now();

        // Create 3 blocked transactions (exactly at threshold)
        Transaction blocked1 = createBlockedTransaction("TXN-B1", "CUST001", now.minusMinutes(25));
        Transaction blocked2 = createBlockedTransaction("TXN-B2", "CUST001", now.minusMinutes(15));
        Transaction blocked3 = createBlockedTransaction("TXN-B3", "CUST001", now.minusMinutes(5));

        // Current transaction being evaluated
        Transaction currentTransaction = Transaction.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("500.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)
                .merchantName("Test Merchant")
                .timestamp(now)
                .build();

        when(transactionRepository.findByCustomerIdAndTimestampBetween(
                eq("CUST001"), any(), any()))
                .thenReturn(List.of(blocked1, blocked2, blocked3));

        FraudRuleResult result = rule.evaluate(currentTransaction);

        assertTrue(result.flagged());
        assertEquals("REPEATED_DECLINED_TRANSACTION", result.ruleName());
        assertEquals(FraudSeverity.CRITICAL, result.severity());
        assertTrue(result.reason().contains("3 blocked transactions"));
        assertTrue(result.reason().contains("30 minutes"));
    }

    @Test
    void shouldFlagWhenExceedingMaxBlockedAttempts() {
        LocalDateTime now = LocalDateTime.now();

        // Create 4 blocked transactions (exceeds threshold)
        Transaction blocked1 = createBlockedTransaction("TXN-B1", "CUST002", now.minusMinutes(28));
        Transaction blocked2 = createBlockedTransaction("TXN-B2", "CUST002", now.minusMinutes(20));
        Transaction blocked3 = createBlockedTransaction("TXN-B3", "CUST002", now.minusMinutes(12));
        Transaction blocked4 = createBlockedTransaction("TXN-B4", "CUST002", now.minusMinutes(5));

        Transaction currentTransaction = Transaction.builder()
                .transactionId("TXN002")
                .customerId("CUST002")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)
                .merchantName("Card Testing")
                .timestamp(now)
                .build();

        when(transactionRepository.findByCustomerIdAndTimestampBetween(
                eq("CUST002"), any(), any()))
                .thenReturn(List.of(blocked1, blocked2, blocked3, blocked4));

        FraudRuleResult result = rule.evaluate(currentTransaction);

        assertTrue(result.flagged());
        assertTrue(result.reason().contains("4 blocked transactions"));
    }

    @Test
    void shouldNotFlagWhenBelowThreshold() {
        LocalDateTime now = LocalDateTime.now();

        // Create only 2 blocked transactions (below threshold of 3)
        Transaction blocked1 = createBlockedTransaction("TXN-B1", "CUST003", now.minusMinutes(20));
        Transaction blocked2 = createBlockedTransaction("TXN-B2", "CUST003", now.minusMinutes(10));

        // Also include an approved transaction to verify filtering
        Transaction approved = Transaction.builder()
                .transactionId("TXN-APPROVED")
                .customerId("CUST003")
                .amount(new BigDecimal("500.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Normal Store")
                .timestamp(now.minusMinutes(15))
                .status(TransactionStatus.APPROVED)
                .build();

        Transaction currentTransaction = Transaction.builder()
                .transactionId("TXN003")
                .customerId("CUST003")
                .amount(new BigDecimal("750.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)
                .merchantName("Online Store")
                .timestamp(now)
                .build();

        when(transactionRepository.findByCustomerIdAndTimestampBetween(
                eq("CUST003"), any(), any()))
                .thenReturn(List.of(blocked1, blocked2, approved));

        FraudRuleResult result = rule.evaluate(currentTransaction);

        assertFalse(result.flagged());
    }

    private Transaction createBlockedTransaction(String txnId, String customerId, LocalDateTime timestamp) {
        return Transaction.builder()
                .transactionId(txnId)
                .customerId(customerId)
                .amount(new BigDecimal("100.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)
                .merchantName("Test Merchant")
                .timestamp(timestamp)
                .status(TransactionStatus.BLOCKED)
                .build();
    }
}