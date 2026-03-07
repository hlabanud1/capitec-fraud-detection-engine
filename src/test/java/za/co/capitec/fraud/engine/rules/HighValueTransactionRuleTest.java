package za.co.capitec.fraud.engine.rules;

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

class HighValueTransactionRuleTest {

    private HighValueTransactionRule rule;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rule = new HighValueTransactionRule(objectMapper);
        ReflectionTestUtils.setField(rule, "highValueThreshold", new BigDecimal("50000.00"));
    }

    @Test
    void shouldFlagHighValueTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN001")
                .customerId("CUST001")
                .amount(new BigDecimal("75000.00"))
                .currency("ZAR")
                .type(TransactionType.EFT_TRANSFER)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertTrue(result.flagged());
        assertEquals("HIGH_VALUE_TRANSACTION", result.ruleName());
        assertNotNull(result.reason());
    }

    @Test
    void shouldNotFlagNormalTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId("TXN002")
                .customerId("CUST001")
                .amount(new BigDecimal("1000.00"))
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_POS)
                .merchantName("Test Merchant")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(transaction);

        assertFalse(result.flagged());
    }

    @Test
    void shouldNotFlagSystemGeneratedTransactionEvenIfLargeAmount() {
        // System-generated types should be skipped entirely, even for large amounts
        Transaction feeCharge = Transaction.builder()
                .transactionId("TXN003")
                .customerId("CUST001")
                .amount(new BigDecimal("100000.00"))  // R100k - above base threshold
                .currency("ZAR")
                .type(TransactionType.FEE_CHARGE)  // System-generated
                .merchantName("Bank")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(feeCharge);

        assertFalse(result.flagged());
        assertEquals("HIGH_VALUE_TRANSACTION", result.ruleName());
    }

    @Test
    void shouldNotFlagInterestCreditEvenIfLargeAmount() {
        // Another system-generated type
        Transaction interestCredit = Transaction.builder()
                .transactionId("TXN004")
                .customerId("CUST001")
                .amount(new BigDecimal("150000.00"))  // R150k - well above base threshold
                .currency("ZAR")
                .type(TransactionType.INTEREST_CREDIT)  // System-generated
                .merchantName("Bank")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(interestCredit);

        assertFalse(result.flagged());
    }

    @Test
    void shouldNotFlagPreAuthorizedTransactionBelowHigherThreshold() {
        // Pre-authorized types use 5× threshold (R250k)
        // R100k is above base (R50k) but below pre-authorized threshold (R250k)
        Transaction salaryDeposit = Transaction.builder()
                .transactionId("TXN005")
                .customerId("CUST001")
                .amount(new BigDecimal("100000.00"))  // R100k
                .currency("ZAR")
                .type(TransactionType.SALARY_DEPOSIT)  // Pre-authorized
                .merchantName("Employer")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(salaryDeposit);

        assertFalse(result.flagged());  // Should NOT flag (below R250k threshold)
    }

    @Test
    void shouldNotFlagDebitOrderBelowHigherThreshold() {
        // Debit order with R200k (above R50k base, below R250k pre-authorized threshold)
        Transaction debitOrder = Transaction.builder()
                .transactionId("TXN006")
                .customerId("CUST001")
                .amount(new BigDecimal("200000.00"))  // R200k
                .currency("ZAR")
                .type(TransactionType.DEBIT_ORDER)  // Pre-authorized
                .merchantName("Insurance Company")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(debitOrder);

        assertFalse(result.flagged());  // Should NOT flag (below R250k threshold)
    }

    @Test
    void shouldFlagHighRiskTransactionAtLowerThreshold() {
        // High-risk types use 0.5× threshold (R25k)
        // R30k should trigger high-risk threshold (above R25k)
        Transaction onlinePurchase = Transaction.builder()
                .transactionId("TXN007")
                .customerId("CUST001")
                .amount(new BigDecimal("30000.00"))  // R30k
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)  // High-risk
                .merchantName("Online Retailer")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(onlinePurchase);

        assertTrue(result.flagged());  // Should flag (above R25k high-risk threshold)
        assertEquals("HIGH_VALUE_TRANSACTION", result.ruleName());
        assertTrue(result.reason().contains("CARD_PURCHASE_ONLINE"));
    }

    @Test
    void shouldFlagInternationalTransferAtLowerThreshold() {
        // International transfer at R40k (above R25k high-risk threshold, below R50k base)
        Transaction internationalTransfer = Transaction.builder()
                .transactionId("TXN008")
                .customerId("CUST001")
                .amount(new BigDecimal("40000.00"))  // R40k
                .currency("ZAR")
                .type(TransactionType.INTERNATIONAL_TRANSFER)  // High-risk
                .merchantName("International Bank")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(internationalTransfer);

        assertTrue(result.flagged());  // Should flag (above R25k high-risk threshold)
        assertTrue(result.reason().contains("INTERNATIONAL_TRANSFER"));
    }

    @Test
    void shouldNotFlagHighRiskTransactionBelowLowerThreshold() {
        // High-risk transaction below R25k threshold
        Transaction onlinePurchase = Transaction.builder()
                .transactionId("TXN009")
                .customerId("CUST001")
                .amount(new BigDecimal("20000.00"))  // R20k (below R25k high-risk threshold)
                .currency("ZAR")
                .type(TransactionType.CARD_PURCHASE_ONLINE)  // High-risk
                .merchantName("Online Retailer")
                .timestamp(LocalDateTime.now())
                .build();

        FraudRuleResult result = rule.evaluate(onlinePurchase);

        assertFalse(result.flagged());  // Should NOT flag (below R25k threshold)
    }
}