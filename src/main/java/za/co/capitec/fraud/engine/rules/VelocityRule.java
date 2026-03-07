package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.engine.FraudRule;
import za.co.capitec.fraud.engine.FraudRuleResult;
import za.co.capitec.fraud.repository.TransactionRepository;
import za.co.capitec.fraud.util.FraudAlertDetailsHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detects suspicious velocity patterns - too many transactions or
 * excessive spending in a short time period.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VelocityRule implements FraudRule {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${fraud.rules.velocity.max-transactions-per-hour:10}")
    private int maxTransactionsPerHour;

    @Value("${fraud.rules.velocity.max-amount-per-hour:100000.00}")
    private BigDecimal maxAmountPerHour;

    @Override
    public FraudRuleResult evaluate(Transaction transaction) {
        LocalDateTime oneHourAgo = transaction.getTimestamp().minusHours(1);

        // Count transactions in the last hour (includes current transaction, already saved by engine)
        long recentTransactionCount = transactionRepository
                .countByCustomerIdSince(transaction.getCustomerId(), oneHourAgo);

        // Sum transaction amounts in the last hour (includes current transaction)
        BigDecimal totalAmount = transactionRepository
                .sumAmountByCustomerIdSince(transaction.getCustomerId(), oneHourAgo);

        // Check transaction count (flag when at or over limit)
        if (recentTransactionCount >= maxTransactionsPerHour) {
            ObjectNode detailsNode = objectMapper.createObjectNode();
            detailsNode.put("transactionCount", recentTransactionCount);
            detailsNode.put("maxAllowed", maxTransactionsPerHour);
            detailsNode.put("timeWindow", "1 hour");
            String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

            return FraudRuleResult.fraud(
                    getRuleName(),
                    FraudSeverity.CRITICAL,
                    "Customer has %d transactions in the last hour (max: %d)".formatted(
                            recentTransactionCount, maxTransactionsPerHour),
                    details
            );
        }

        // Check total amount (totalAmount already includes current transaction)
        if (totalAmount.compareTo(maxAmountPerHour) > 0) {
            ObjectNode detailsNode = objectMapper.createObjectNode();
            detailsNode.put("totalAmount", totalAmount.toString());
            detailsNode.put("maxAllowed", maxAmountPerHour.toString());
            detailsNode.put("timeWindow", "1 hour");
            detailsNode.put("currency", transaction.getCurrency());
            String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

            return FraudRuleResult.fraud(
                    getRuleName(),
                    FraudSeverity.CRITICAL,
                    "Total transaction amount %s in the last hour exceeds limit of %s".formatted(
                            totalAmount, maxAmountPerHour),
                    details
            );
        }

        return FraudRuleResult.noFraud(getRuleName());
    }

    @Override
    public String getRuleName() {
        return "VELOCITY_CHECK";
    }

    @Override
    public int getPriority() {
        return 90;
    }
}
