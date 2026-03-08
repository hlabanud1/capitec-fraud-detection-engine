package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionType;
import za.co.capitec.fraud.engine.FraudRule;
import za.co.capitec.fraud.engine.FraudRuleResult;
import za.co.capitec.fraud.util.FraudAlertDetailsHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Detects unusually high-value transactions that may indicate fraud.
 * Uses type-aware logic to skip system-generated transactions and apply different
 * thresholds based on transaction risk profile.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HighValueTransactionRule implements FraudRule {

    private final ObjectMapper objectMapper;

    @Value("${fraud.rules.high-value-threshold:50000.00}")
    private BigDecimal highValueThreshold;

    // Threshold multipliers
    private static final BigDecimal CRITICAL_VALUE_MULTIPLIER = new BigDecimal("200");  // R10M for RTGS
    private static final BigDecimal INSTANT_IRREVOCABLE_MULTIPLIER = new BigDecimal("10");  // R500k for RTC
    private static final BigDecimal PRE_AUTHORIZED_MULTIPLIER = new BigDecimal("5");  // R250k for debit orders
    private static final BigDecimal HIGH_RISK_MULTIPLIER = new BigDecimal("0.5");  // R25k for online card
    private static final BigDecimal CRITICAL_RISK_MULTIPLIER = new BigDecimal("0.3");  // R15k for international
    private static final BigDecimal STANDARD_MULTIPLIER = BigDecimal.ONE;  // R50k base

    // System-generated types: NOT user-initiated, skip fraud checks entirely
    private static final Set<TransactionType> SYSTEM_GENERATED_TYPES = Set.of(
            TransactionType.FEE_CHARGE,
            TransactionType.INTEREST_CREDIT,
            TransactionType.REVERSAL
    );

    // Critical-value types: Very high threshold (R10M+) - legitimate large transactions
    private static final Set<TransactionType> CRITICAL_VALUE_TYPES = Set.of(
            TransactionType.RTGS_TRANSFER
    );

    // Pre-authorized types: High threshold (R250k) - already verified during setup
    private static final Set<TransactionType> PRE_AUTHORIZED_TYPES = Set.of(
            TransactionType.DEBIT_ORDER,
            TransactionType.RECURRING_PAYMENT,
            TransactionType.SALARY_DEPOSIT
    );

    // Instant irrevocable types: Elevated threshold (R500k) - RTC requires monitoring but allow higher amounts
    private static final Set<TransactionType> INSTANT_IRREVOCABLE_TYPES = Set.of(
            TransactionType.RTC_TRANSFER  // Real-Time Clearing (instant, can't reverse after settlement)
    );

    // Critical-risk types: Very low threshold (R15k) - highest fraud rates
    private static final Set<TransactionType> CRITICAL_RISK_TYPES = Set.of(
            TransactionType.INTERNATIONAL_TRANSFER,  // Cross-border fraud, sanctions risk
            TransactionType.CARD_CASH_ADVANCE        // Often indicates fraud or financial distress
    );

    // High-risk types: Low threshold (R25k) - card-not-present fraud common
    private static final Set<TransactionType> HIGH_RISK_TYPES = Set.of(
            TransactionType.CARD_PURCHASE_ONLINE
    );

    // Centralized multiplier mapping for all transaction types
    // Makes threshold configuration easier to maintain and extend
    private static final Map<TransactionType, BigDecimal> TYPE_MULTIPLIERS = Map.ofEntries(
            // Critical value transactions (200x)
            Map.entry(TransactionType.RTGS_TRANSFER, CRITICAL_VALUE_MULTIPLIER),

            // Instant irrevocable transactions (10x)
            Map.entry(TransactionType.RTC_TRANSFER, INSTANT_IRREVOCABLE_MULTIPLIER),

            // Pre-authorized transactions (5x)
            Map.entry(TransactionType.DEBIT_ORDER, PRE_AUTHORIZED_MULTIPLIER),
            Map.entry(TransactionType.RECURRING_PAYMENT, PRE_AUTHORIZED_MULTIPLIER),
            Map.entry(TransactionType.SALARY_DEPOSIT, PRE_AUTHORIZED_MULTIPLIER),

            // Critical risk transactions (0.3x)
            Map.entry(TransactionType.INTERNATIONAL_TRANSFER, CRITICAL_RISK_MULTIPLIER),
            Map.entry(TransactionType.CARD_CASH_ADVANCE, CRITICAL_RISK_MULTIPLIER),

            // High risk transactions (0.5x)
            Map.entry(TransactionType.CARD_PURCHASE_ONLINE, HIGH_RISK_MULTIPLIER)
    );

    @Override
    public FraudRuleResult evaluate(Transaction transaction) {
        // Skip system-generated transactions (bank-initiated, not fraud)
        if (SYSTEM_GENERATED_TYPES.contains(transaction.getType())) {
            log.debug("Skipping high-value check for system-generated type: {}",
                    transaction.getType());
            return FraudRuleResult.noFraud(getRuleName());
        }

        // Calculate type-aware threshold
        BigDecimal effectiveThreshold = calculateThreshold(transaction.getType());

        if (transaction.getAmount().compareTo(effectiveThreshold) > 0) {
            ObjectNode detailsNode = objectMapper.createObjectNode();
            detailsNode.put("amount", transaction.getAmount().toString());
            detailsNode.put("baseThreshold", highValueThreshold.toString());
            detailsNode.put("effectiveThreshold", effectiveThreshold.toString());
            detailsNode.put("transactionType", transaction.getType().name());
            detailsNode.put("currency", transaction.getCurrency());
            String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

            return FraudRuleResult.fraud(
                    getRuleName(),
                    FraudSeverity.HIGH,
                    "Transaction amount %s %s exceeds threshold %s for type %s".formatted(
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            effectiveThreshold,
                            transaction.getType()),
                    details
            );
        }

        return FraudRuleResult.noFraud(getRuleName());
    }

    /**
     * Calculate the effective threshold for a given transaction type.
     * Uses a Map lookup for cleaner, more maintainable code.
     *
     * @param type the transaction type
     * @return the effective threshold (base threshold × type-specific multiplier)
     */
    private BigDecimal calculateThreshold(TransactionType type) {
        BigDecimal multiplier = TYPE_MULTIPLIERS.getOrDefault(type, STANDARD_MULTIPLIER);
        return highValueThreshold.multiply(multiplier);
    }

    @Override
    public String getRuleName() {
        return "HIGH_VALUE_TRANSACTION";
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
