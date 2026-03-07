package za.co.capitec.fraud.controller;

import za.co.capitec.fraud.dto.TransactionRequest;
import za.co.capitec.fraud.dto.TransactionResponse;
import za.co.capitec.fraud.service.FraudDetectionService;
import za.co.capitec.fraud.util.PiiMaskingUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for transaction processing and fraud detection.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Transaction processing and fraud detection endpoints")
public class TransactionController {

    private final FraudDetectionService fraudDetectionService;

    @PostMapping
    @Operation(
            summary = "Process a transaction",
            description = "Submit a transaction for fraud detection evaluation. " +
                    "The transaction will be evaluated against all configured fraud rules. " +
                    "This endpoint is idempotent - submitting the same transactionId multiple times " +
                    "will return the existing result without reprocessing.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Transaction processed successfully",
                            content = @Content(schema = @Schema(implementation = TransactionResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid transaction data"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Duplicate transaction ID - transaction with this ID already exists"
                    )
            }
    )
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request) {

        log.info("Processing transaction: {} for customer: {}",
                request.getTransactionId(), PiiMaskingUtil.maskCustomerId(request.getCustomerId()));

        TransactionResponse response = fraudDetectionService.processTransaction(request);

        log.info("Transaction {} processed with status: {}",
                request.getTransactionId(), response.status());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Check if the transaction service is running"
    )
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transaction service is running");
    }
}
