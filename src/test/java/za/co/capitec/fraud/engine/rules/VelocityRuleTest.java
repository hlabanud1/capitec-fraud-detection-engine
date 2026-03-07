package za.co.capitec.fraud.engine.rules;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private ObjectMapper objectMapper;
    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new VelocityRule(transactionRepository, objectMapper);
        ReflectionTestUtils.setField(rule, "maxTransactionsPerHour", 10);
        ReflectionTestUtils.setField(rule, "maxAmountPerHour", new BigDecimal("100000.00"));
    }

    @Test
    void shouldFlagExcessiveTransactionCount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        when(transactionRepository.countByCustomerIdSince(eq("CUST001"), any()))
                .thenReturn(10L);
        when(transactionRepository.sumAmountByCustomerIdSince(eq("CUST001"), any()))
                .thenReturn(new BigDecimal("50000.00"));

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());
        assertEquals("VELOCITY_CHECK", result.ruleName());
    }

    @Test
    void shouldFlagExcessiveAmount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN002")
                .customerId("CUST002")
                .amount(new BigDecimal("60000.00"))
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        when(transactionRepository.countByCustomerIdSince(eq("CUST002"), any()))
                .thenReturn(3L);
        // Sum includes current transaction (saved by engine before rules run); 110000 > 100000 limit
        when(transactionRepository.sumAmountByCustomerIdSince(eq("CUST002"), any()))
                .thenReturn(new BigDecimal("110000.00"));

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());
    }

    @Test
    void shouldNotFlagNormalVelocity() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN003")
                .customerId("CUST003")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        when(transactionRepository.countByCustomerIdSince(eq("CUST003"), any()))
                .thenReturn(2L);
        when(transactionRepository.sumAmountByCustomerIdSince(eq("CUST003"), any()))
                .thenReturn(new BigDecimal("3000.00"));

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());
    }
}