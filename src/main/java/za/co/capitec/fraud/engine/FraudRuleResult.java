package za.co.capitec.fraud.engine;

import za.co.capitec.fraud.domain.FraudSeverity;

/**
 * Result of evaluating a fraud rule against a transaction.
 */
public record FraudRuleResult(
        boolean flagged,
        String ruleName,
        FraudSeverity severity,
        String reason,
        int riskScore,
        String details
) {
    /**
     * Create a result indicating no fraud was detected.
     */
    public static FraudRuleResult noFraud(String ruleName) {
        return new FraudRuleResult(
                false,
                ruleName,
                FraudSeverity.LOW,
                "No fraud detected",
                0,
                "{}"
        );
    }

    /**
     * Create a result indicating fraud was detected.
     */
    public static FraudRuleResult fraud(String ruleName, FraudSeverity severity,
                                        String reason, String details) {
        return new FraudRuleResult(
                true,
                ruleName,
                severity,
                reason,
                severity.getBaseScore(),
                details
        );
    }
}
