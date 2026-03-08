package za.co.capitec.fraud.engine.rules;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionStatus;
import za.co.capitec.fraud.engine.FraudRule;
import za.co.capitec.fraud.engine.FraudRuleResult;
import za.co.capitec.fraud.repository.TransactionRepository;
import za.co.capitec.fraud.util.FraudAlertDetailsHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detects patterns of repeated transaction attempts after blocks/declines,
 * which may indicate card testing or brute force attacks.
 *
 * <p>This rule flags when a customer already has ≥3 transactions with status
 * {@link TransactionStatus#BLOCKED} in the last 30 minutes. These are transactions
 * previously blocked by the fraud engine due to critical alerts, not external
 * payment gateway declines.
 *
 * <p><b>Detection Strategy</b>: Multiple blocked transactions in quick succession
 * suggest an attacker is testing stolen card details or attempting to circumvent
 * fraud controls through repeated attempts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepeatedDeclinedTransactionRule implements FraudRule {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    private static final int TIME_WINDOW_MINUTES = 30;
    private static final int MAX_BLOCKED_ATTEMPTS = 3;

    @Override
    public FraudRuleResult evaluate(Transaction transaction) {
        LocalDateTime windowStart = transaction.getTimestamp().minusMinutes(TIME_WINDOW_MINUTES);

        List<Transaction> recentTransactions = transactionRepository
                .findByCustomerIdAndTimestampBetween(
                        transaction.getCustomerId(),
                        windowStart,
                        transaction.getTimestamp()
                );

        long blockedCount = recentTransactions.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.BLOCKED)
                .count();

        if (blockedCount >= MAX_BLOCKED_ATTEMPTS) {
            ObjectNode detailsNode = objectMapper.createObjectNode();
            detailsNode.put("blockedAttempts", blockedCount);
            detailsNode.put("timeWindowMinutes", TIME_WINDOW_MINUTES);
            detailsNode.put("maxAllowed", MAX_BLOCKED_ATTEMPTS);
            String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

            return FraudRuleResult.fraud(
                    getRuleName(),
                    FraudSeverity.CRITICAL,
                    "Customer has %d blocked transactions in the last %d minutes".formatted(
                            blockedCount, TIME_WINDOW_MINUTES),
                    details
            );
        }

        return FraudRuleResult.noFraud(getRuleName());
    }

    @Override
    public String getRuleName() {
        return "REPEATED_DECLINED_TRANSACTION";
    }

    @Override
    public int getPriority() {
        return 70;
    }
}
