package za.co.capitec.fraud.dto;

import za.co.capitec.fraud.domain.TransactionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "Transaction ID is required")
    @Size(max = 255, message = "Transaction ID must not exceed 255 characters")
    private String transactionId;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 255, message = "Customer ID must not exceed 255 characters")
    private String customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotBlank(message = "Merchant name is required")
    @Size(max = 255, message = "Merchant name must not exceed 255 characters")
    private String merchantName;

    @Size(max = 255, message = "Merchant ID must not exceed 255 characters")
    private String merchantId;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    private LocalDateTime timestamp;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 45, message = "IP address must not exceed 45 characters")
    private String ipAddress;

    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;
}
