package za.co.capitec.fraud.service;

import za.co.capitec.fraud.domain.FraudAlert;
import za.co.capitec.fraud.domain.Transaction;
import za.co.capitec.fraud.dto.FraudAlertResponse;
import za.co.capitec.fraud.dto.TransactionRequest;
import za.co.capitec.fraud.dto.TransactionResponse;
import za.co.capitec.fraud.engine.FraudDetectionEngine;
import za.co.capitec.fraud.repository.FraudAlertRepository;
import za.co.capitec.fraud.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for fraud detection operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final FraudDetectionEngine fraudDetectionEngine;
    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Find existing transaction by transactionId for idempotency.
     * Returns the complete response if transaction exists, null otherwise.
     */
    @Transactional(readOnly = true)
    public TransactionResponse findExistingTransaction(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .map(transaction -> {
                    List<FraudAlert> alerts = fraudAlertRepository
                            .findByTransaction_Id(transaction.getId());
                    return buildResponse(transaction, alerts);
                })
                .orElse(null);
    }

    /**
     * Process a transaction through the fraud detection engine.
     * This method is idempotent - if a transaction with the same transactionId already exists,
     * it returns the existing result without reprocessing.
     */
    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        // Check for existing transaction (idempotency)
        TransactionResponse existingResponse = findExistingTransaction(request.getTransactionId());
        if (existingResponse != null) {
            log.info("Duplicate transaction ID: {} - returning existing result (idempotent)",
                    request.getTransactionId());
            return existingResponse;
        }

        // Process new transaction
        Transaction transaction = mapToTransaction(request);
        List<FraudAlert> alerts = fraudDetectionEngine.processTransaction(transaction);
        return buildResponse(transaction, alerts);
    }

    /**
     * Build a TransactionResponse from a transaction and its fraud alerts.
     * Centralizes response building logic to avoid duplication.
     */
    private TransactionResponse buildResponse(Transaction transaction, List<FraudAlert> alerts) {
        var alertResponses = alerts.stream()
                .map(this::mapToAlertResponse)
                .toList();

        var message = alerts.isEmpty()
                ? "Transaction approved - no fraud detected"
                : "Transaction flagged - %d fraud alert(s) generated".formatted(alerts.size());

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getCustomerId(),
                transaction.getType(),
                transaction.getStatus(),
                alerts.size(),
                alertResponses,
                message
        );
    }

    /**
     * Retrieve fraud alerts for a specific customer.
     */
    @Transactional(readOnly = true)
    public List<FraudAlertResponse> getAlertsByCustomer(String customerId) {
        List<FraudAlert> alerts = fraudAlertRepository.findByCustomerId(customerId);
        return alerts.stream()
                .map(this::mapToAlertResponse)
                .toList();
    }

    /**
     * Retrieve recent fraud alerts.
     */
    @Transactional(readOnly = true)
    public List<FraudAlertResponse> getRecentAlerts(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<FraudAlert> alerts = fraudAlertRepository.findRecentAlerts(since);
        return alerts.stream()
                .map(this::mapToAlertResponse)
                .toList();
    }

    /**
     * Retrieve fraud alerts for a specific customer with pagination.
     */
    @Transactional(readOnly = true)
    public Page<FraudAlertResponse> getAlertsByCustomerPaginated(String customerId, Pageable pageable) {
        Page<FraudAlert> alerts = fraudAlertRepository.findByCustomerIdPaginated(customerId, pageable);
        return alerts.map(this::mapToAlertResponse);
    }

    /**
     * Retrieve recent fraud alerts with pagination.
     */
    @Transactional(readOnly = true)
    public Page<FraudAlertResponse> getRecentAlertsPaginated(int hours, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Page<FraudAlert> alerts = fraudAlertRepository.findRecentAlertsPaginated(since, pageable);
        return alerts.map(this::mapToAlertResponse);
    }

    private Transaction mapToTransaction(TransactionRequest request) {
        return Transaction.builder()
                .transactionId(request.getTransactionId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .merchantName(request.getMerchantName())
                .merchantId(request.getMerchantId())
                .location(request.getLocation())
                .timestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now())
                .description(request.getDescription())
                .ipAddress(request.getIpAddress())
                .deviceId(request.getDeviceId())
                .build();
    }

    private FraudAlertResponse mapToAlertResponse(FraudAlert alert) {
        return new FraudAlertResponse(
                alert.getId(),
                alert.getTransaction().getTransactionId(),
                alert.getTransaction().getCustomerId(),
                alert.getTransaction().getType(),
                alert.getRuleName(),
                alert.getSeverity(),
                alert.getReason(),
                alert.getRiskScore(),
                alert.getDetails(),
                alert.getCreatedAt()
        );
    }
}
