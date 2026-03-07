package za.co.capitec.fraud.dto;

import za.co.capitec.fraud.domain.TransactionStatus;
import za.co.capitec.fraud.domain.TransactionType;

import java.util.List;

/**
 * DTO for transaction processing response.
 */
public record TransactionResponse(
        String transactionId,
        String customerId,
        TransactionType type,
        TransactionStatus status,
        int alertCount,
        List<FraudAlertResponse> alerts,
        String message
) {}
