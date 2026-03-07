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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Detects impossible travel patterns - transactions from geographically
 * distant locations within a short time period.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RapidLocationChangeRule implements FraudRule {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    private static final int TIME_WINDOW_HOURS = 2;
    private static final int TIME_WINDOW_MINUTES = TIME_WINDOW_HOURS * 60;
    private static final List<String> KNOWN_HIGH_RISK_LOCATIONS = List.of(
            "Unknown", "TOR Exit Node", "VPN", "Proxy"
    );

    @Override
    public FraudRuleResult evaluate(Transaction transaction) {
        if (transaction.getLocation() == null) {
            return FraudRuleResult.noFraud(getRuleName());
        }

        // Check for high-risk location patterns
        for (String highRiskPattern : KNOWN_HIGH_RISK_LOCATIONS) {
            if (transaction.getLocation().contains(highRiskPattern)) {
                ObjectNode detailsNode = objectMapper.createObjectNode();
                detailsNode.put("location", transaction.getLocation());
                detailsNode.put("pattern", highRiskPattern);
                String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

                return FraudRuleResult.fraud(
                        getRuleName(),
                        FraudSeverity.HIGH,
                        "Transaction from high-risk location: %s".formatted(transaction.getLocation()),
                        details
                );
            }
        }

        // Check for rapid location changes
        LocalDateTime windowStart = transaction.getTimestamp().minusHours(TIME_WINDOW_HOURS);
        List<Transaction> recentTransactions = transactionRepository
                .findByCustomerIdAndTimestampBetween(
                        transaction.getCustomerId(),
                        windowStart,
                        transaction.getTimestamp()
                );

        for (Transaction recentTransaction : recentTransactions) {
            if (recentTransaction.getLocation() != null &&
                    !recentTransaction.getLocation().equals(transaction.getLocation())) {

                long minutesBetween = ChronoUnit.MINUTES.between(
                        recentTransaction.getTimestamp(),
                        transaction.getTimestamp()
                );

                // Flag if location changed within time window
                if (minutesBetween < TIME_WINDOW_MINUTES) {
                    ObjectNode detailsNode = objectMapper.createObjectNode();
                    detailsNode.put("previousLocation", recentTransaction.getLocation());
                    detailsNode.put("currentLocation", transaction.getLocation());
                    detailsNode.put("minutesBetween", minutesBetween);
                    detailsNode.put("previousTransactionId", recentTransaction.getTransactionId());
                    String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

                    return FraudRuleResult.fraud(
                            getRuleName(),
                            FraudSeverity.HIGH,
                            "Rapid location change from %s to %s in %d minutes".formatted(
                                    recentTransaction.getLocation(), transaction.getLocation(), minutesBetween),
                            details
                    );
                }
            }
        }

        return FraudRuleResult.noFraud(getRuleName());
    }

    @Override
    public String getRuleName() {
        return "RAPID_LOCATION_CHANGE";
    }

    @Override
    public int getPriority() {
        return 80;
    }
}
