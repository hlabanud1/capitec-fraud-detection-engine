package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.engine.FraudRuleResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UnusualTimeRuleTest {

    private ObjectMapper objectMapper;
    private UnusualTimeRule rule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new UnusualTimeRule(objectMapper);
        ReflectionTestUtils.setField(rule, "unusualTimeStartHour", 2);
        ReflectionTestUtils.setField(rule, "unusualTimeEndHour", 5);
        ReflectionTestUtils.setField(rule, "significantAmount", new BigDecimal("10000.00"));
    }

    @Test
    void shouldFlagSignificantTransactionDuringUnusualHours() {
        // Create transaction at 03:00 (unusual hour) with significant amount
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 3, 30);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("15000.00"))  // Above 10000 threshold
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Night Transfer")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());
        assertEquals("UNUSUAL_TIME", result.ruleName());
        assertEquals(FraudSeverity.MEDIUM, result.severity());
        assertTrue(result.reason().contains("Significant"));
        assertTrue(result.reason().contains("EFT_TRANSFER"));
        assertTrue(result.reason().contains("03:00"));
    }

    @Test
    void shouldNotFlagSmallTransactionDuringUnusualHours() {
        // Create transaction at 03:00 (unusual hour) but with small amount
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 3, 30);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN002")
                .customerId("CUST002")
                .amount(new BigDecimal("500.00"))  // Below 10000 threshold
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("24h Store")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());
    }

    @Test
    void shouldNotFlagSignificantTransactionDuringNormalHours() {
        // Create transaction at 14:00 (normal hour) with significant amount
        LocalDateTime normalTime = LocalDateTime.of(2026, 3, 5, 14, 30);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN003")
                .customerId("CUST003")
                .amount(new BigDecimal("25000.00"))  // Above 10000 threshold
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Business Transfer")
                .timestamp(normalTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());
    }

    @Test
    void shouldNotFlagAutomatedDebitOrderAtUnusualHour() {
        // Debit orders are automated - expected to run at unusual hours
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 3, 0);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN004")
                .customerId("CUST004")
                .amount(new BigDecimal("50000.00"))  // Large amount, above 10000 threshold
                .currency("ZAR")
                .type(TransactionType.DEBIT_ORDER)  // Automated type
                .merchantName("Insurance Company")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());  // Should NOT flag (automated type)
    }

    @Test
    void shouldNotFlagSalaryDepositAtUnusualHour() {
        // Salary deposits often run at night (payroll processing)
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 2, 30);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN005")
                .customerId("CUST005")
                .amount(new BigDecimal("100000.00"))  // Very large amount
                .currency("ZAR")
                .type(TransactionType.SALARY_DEPOSIT)  // Automated type
                .merchantName("Employer")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());  // Should NOT flag (automated type)
    }

    @Test
    void shouldNotFlagFeeChargeAtUnusualHour() {
        // Bank fees are system-generated, can occur at month-end (unusual hours)
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 4, 0);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN006")
                .customerId("CUST006")
                .amount(new BigDecimal("15000.00"))  // Above 10000 threshold
                .currency("ZAR")
                .type(TransactionType.FEE_CHARGE)  // System-generated/automated type
                .merchantName("Bank")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());  // Should NOT flag (system-generated type)
    }

    @Test
    void shouldNotFlagRecurringPaymentAtUnusualHour() {
        // Recurring payments are scheduled, can run at any hour
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 3, 45);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN007")
                .customerId("CUST007")
                .amount(new BigDecimal("20000.00"))  // Above 10000 threshold
                .currency("ZAR")
                .type(TransactionType.RECURRING_PAYMENT)  // Automated type
                .merchantName("Subscription Service")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());  // Should NOT flag (automated type)
    }

    @Test
    void shouldFlagUserInitiatedTransactionAtUnusualHour() {
        // User-initiated transactions (not automated) at unusual hours should be flagged
        LocalDateTime unusualTime = LocalDateTime.of(2026, 3, 5, 3, 15);

        Transaction transaction = Transaction.builder()
                .transactionId("TXN008")
                .customerId("CUST008")
                .amount(new BigDecimal("50000.00"))  // Significant amount
                .currency("ZAR")
                .type(TransactionType.INTERNAL_TRANSFER)  // User-initiated (not automated)
                .merchantName("Another Account")
                .timestamp(unusualTime)
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());  // Should flag (user-initiated at unusual hour)
        assertEquals("UNUSUAL_TIME", result.ruleName());
        assertEquals(FraudSeverity.MEDIUM, result.severity());
    }
}