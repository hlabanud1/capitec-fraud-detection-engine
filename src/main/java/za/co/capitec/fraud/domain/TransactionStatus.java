package za.co.capitec.fraud.domain;

/**
 * Status of a transaction in the fraud detection system.
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    FLAGGED,
    BLOCKED
}
