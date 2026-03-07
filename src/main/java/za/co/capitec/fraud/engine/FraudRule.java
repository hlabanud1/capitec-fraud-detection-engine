package za.co.capitec.fraud.engine;

import za.co.capitec.fraud.domain.Transaction;

/**
 * Interface for fraud detection rules.
 * Each rule evaluates a transaction and returns a result indicating
 * whether the transaction is suspicious.
 */
public interface FraudRule {

    FraudRuleResult evaluate(Transaction transaction);
    String getRuleName();
    default int getPriority() {
        return 0;
    }
}
