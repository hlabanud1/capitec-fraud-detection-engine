package za.co.capitec.fraud.engine;

import org.springframework.dao.DataAccessException;
import za.co.capitec.fraud.domain.FraudAlert;
import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.domain.TransactionStatus;
import za.co.capitec.fraud.repository.FraudAlertRepository;
import za.co.capitec.fraud.repository.TransactionRepository;
import za.co.capitec.fraud.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core fraud detection engine that orchestrates the evaluation of transactions
 * against multiple fraud rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionEngine {

    private final List<FraudRule> fraudRules;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;

    /**
     * Process a transaction through all fraud detection rules.
     */
    @Transactional
    public List<FraudAlert> processTransaction(Transaction transaction) {
        log.info("Processing transaction {} for customer {}",
                transaction.getTransactionId(), PiiMaskingUtil.maskCustomerId(transaction.getCustomerId()));

        // Save the transaction first
        Transaction savedTransaction = transactionRepository.save(transaction);

        List<FraudAlert> alerts = new ArrayList<>();

        // Evaluate all rules in priority order (highest priority first)
        var rulesByPriority = fraudRules.stream()
                .sorted(Comparator.comparingInt(FraudRule::getPriority).reversed())
                .toList();

        for (var rule : rulesByPriority) {
            try {
                var result = rule.evaluate(savedTransaction);

                if (result.flagged()) {
                    log.warn("Transaction {} flagged by rule {}: {}",
                            transaction.getTransactionId(),
                            rule.getRuleName(),
                            result.reason());

                    var alert = createAlert(savedTransaction, result);
                    alerts.add(alert);
                }
            } catch (Exception e) {
                handleRuleEvaluationError(rule, transaction.getTransactionId(), e);
            }
        }

        // Batch save all alerts
        if (!alerts.isEmpty()) {
            alerts = new ArrayList<>(fraudAlertRepository.saveAll(alerts));
        }

        // Update transaction status based on alerts
        if (!alerts.isEmpty()) {
            var hasCritical = alerts.stream()
                    .anyMatch(alert -> alert.getSeverity() == FraudSeverity.CRITICAL);
            savedTransaction.setStatus(hasCritical ? TransactionStatus.BLOCKED : TransactionStatus.FLAGGED);
        } else {
            savedTransaction.setStatus(TransactionStatus.APPROVED);
        }

        transactionRepository.save(savedTransaction);

        log.info("Transaction {} processed: {} alerts generated, status: {}",
                transaction.getTransactionId(), alerts.size(), savedTransaction.getStatus());

        return alerts;
    }

    private FraudAlert createAlert(Transaction transaction, FraudRuleResult result) {
        return FraudAlert.builder()
                .transaction(transaction)
                .ruleName(result.ruleName())
                .severity(result.severity())
                .reason(result.reason())
                .riskScore(result.riskScore())
                .details(result.details())
                .build();
    }

    private void handleRuleEvaluationError(FraudRule rule, String transactionId, Exception e) {
        switch (e) {
            case IllegalArgumentException ex ->
                    log.error("Rule {} failed for transaction {} due to validation error: {}",
                            rule.getRuleName(), transactionId, ex.getMessage());
            case IllegalStateException ex ->
                    log.error("Rule {} failed for transaction {} due to validation error: {}",
                            rule.getRuleName(), transactionId, ex.getMessage());
            case DataAccessException dbEx ->
                    log.error("Database error in rule {} for transaction {}: {}",
                            rule.getRuleName(), transactionId, dbEx.getMessage());
            default ->
                    log.error("Unexpected error evaluating rule {} for transaction {}. This may indicate a bug.",
                            rule.getRuleName(), transactionId, e);
        }
    }
}
