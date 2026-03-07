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
import java.util.Set;

/**
 * Detects transactions occurring at unusual times (e.g., late night hours)
 * which may indicate compromised accounts.
 * Uses type-aware logic to skip automated/scheduled transactions that are expected
 * to run at any hour.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UnusualTimeRule implements FraudRule {

    private final ObjectMapper objectMapper;

    @Value("${fraud.rules.unusual-time.start-hour:2}")
    private int unusualTimeStartHour;

    @Value("${fraud.rules.unusual-time.end-hour:5}")
    private int unusualTimeEndHour;

    @Value("${fraud.rules.unusual-time.significant-amount:10000.00}")
    private BigDecimal significantAmount;

    // Types expected to run at unusual hours (automated, scheduled)
    private static final Set<TransactionType> AUTOMATED_TYPES = Set.of(
            TransactionType.DEBIT_ORDER,        // Pre-authorized, runs overnight
            TransactionType.RECURRING_PAYMENT,  // Scheduled, runs anytime
            TransactionType.FEE_CHARGE,         // System-generated, month-end
            TransactionType.INTEREST_CREDIT,    // System-generated, month-end
            TransactionType.SALARY_DEPOSIT,     // Payroll, often runs at night
            TransactionType.REVERSAL            // System-generated, can occur anytime
    );

    @Override
    public FraudRuleResult evaluate(Transaction transaction) {
        // Skip automated types (expected to run at any hour)
        if (AUTOMATED_TYPES.contains(transaction.getType())) {
            log.debug("Skipping unusual-time check for automated type: {}",
                    transaction.getType());
            return FraudRuleResult.noFraud(getRuleName());
        }

        int hour = transaction.getTimestamp().getHour();

        // Only flag significant user-initiated transactions during unusual hours
        if (hour >= unusualTimeStartHour && hour < unusualTimeEndHour) {
            if (transaction.getAmount().compareTo(significantAmount) >= 0) {
                ObjectNode detailsNode = objectMapper.createObjectNode();
                detailsNode.put("hour", hour);
                detailsNode.put("amount", transaction.getAmount().toString());
                detailsNode.put("transactionType", transaction.getType().name());
                detailsNode.put("unusualTimeWindow",
                        "%02d:00-%02d:00".formatted(unusualTimeStartHour, unusualTimeEndHour));
                String details = FraudAlertDetailsHelper.toJson(detailsNode, objectMapper);

                return FraudRuleResult.fraud(
                        getRuleName(),
                        FraudSeverity.MEDIUM,
                        "Significant %s transaction (%s) at unusual hour: %02d:00".formatted(
                                transaction.getType(),
                                transaction.getAmount(),
                                hour),
                        details
                );
            }
        }

        return FraudRuleResult.noFraud(getRuleName());
    }

    @Override
    public String getRuleName() {
        return "UNUSUAL_TIME";
    }

    @Override
    public int getPriority() {
        return 50;
    }
}
