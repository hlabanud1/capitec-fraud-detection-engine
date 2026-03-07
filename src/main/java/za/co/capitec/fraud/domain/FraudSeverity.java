package za.co.capitec.fraud.domain;

/**
 * Severity levels for fraud alerts.
 */
public enum FraudSeverity {
    LOW(10),
    MEDIUM(50),
    HIGH(75),
    CRITICAL(100);

    private final int baseScore;

    FraudSeverity(int baseScore) {
        this.baseScore = baseScore;
    }

    public int getBaseScore() {
        return baseScore;
    }
}
