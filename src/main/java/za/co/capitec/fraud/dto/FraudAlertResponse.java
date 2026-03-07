package za.co.capitec.fraud.dto;

import za.co.capitec.fraud.domain.FraudSeverity;
import za.co.capitec.fraud.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

/**
 * DTO for fraud alert response.
 */
public record FraudAlertResponse(
        Long id,
        String transactionId,
        String customerId,
        TransactionType transactionType,
        String ruleName,
        FraudSeverity severity,
        String reason,
        Integer riskScore,
        @JsonRawValue
        @JsonDeserialize(using = RawJsonDeserializer.class)
        String details,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {}
