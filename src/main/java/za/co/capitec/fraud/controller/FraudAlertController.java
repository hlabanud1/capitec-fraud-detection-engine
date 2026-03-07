package za.co.capitec.fraud.controller;

import org.springframework.data.domain.Sort;
import za.co.capitec.fraud.dto.FraudAlertResponse;
import za.co.capitec.fraud.service.FraudDetectionService;
import za.co.capitec.fraud.util.PiiMaskingUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for retrieving fraud alerts.
 */
@RestController
@RequestMapping("/api/v1/fraud-alerts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Fraud Alerts", description = "Fraud alert retrieval endpoints")
public class FraudAlertController {

    private final FraudDetectionService fraudDetectionService;

    @GetMapping("/customer/{customerId}")
    @Operation(
            summary = "Get fraud alerts for a customer",
            description = "Retrieve all fraud alerts associated with a specific customer ID",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Alerts retrieved successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FraudAlertResponse.class)))
                    )
            }
    )
    public ResponseEntity<List<FraudAlertResponse>> getAlertsByCustomer(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable @NotBlank @Size(max = 255) String customerId) {

        log.info("Retrieving fraud alerts for customer: {}", PiiMaskingUtil.maskCustomerId(customerId));
        List<FraudAlertResponse> alerts = fraudDetectionService.getAlertsByCustomer(customerId);
        log.info("Found {} alerts for customer: {}", alerts.size(), PiiMaskingUtil.maskCustomerId(customerId));

        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/recent")
    @Operation(
            summary = "Get recent fraud alerts",
            description = "Retrieve fraud alerts from the last N hours",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Alerts retrieved successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FraudAlertResponse.class)))
                    )
            }
    )
    public ResponseEntity<List<FraudAlertResponse>> getRecentAlerts(
            @Parameter(description = "Number of hours to look back (1-720)", example = "24")
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {

        log.info("Retrieving fraud alerts from the last {} hours", hours);
        List<FraudAlertResponse> alerts = fraudDetectionService.getRecentAlerts(hours);
        log.info("Found {} recent alerts", alerts.size());

        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/customer/{customerId}/paginated")
    @Operation(
            summary = "Get fraud alerts for a customer (paginated)",
            description = "Retrieve fraud alerts for a customer with pagination support to avoid large responses",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Paginated alerts retrieved successfully",
                            content = @Content(schema = @Schema(implementation = Page.class))
                    )
            }
    )
    public ResponseEntity<Page<FraudAlertResponse>> getAlertsByCustomerPaginated(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable @NotBlank @Size(max = 255) String customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Retrieving paginated fraud alerts for customer: {} (page: {}, size: {})",
                PiiMaskingUtil.maskCustomerId(customerId), pageable.getPageNumber(), pageable.getPageSize());
        Page<FraudAlertResponse> alerts = fraudDetectionService.getAlertsByCustomerPaginated(customerId, pageable);
        log.info("Found {} alerts for customer: {} (total: {}, pages: {})",
                alerts.getNumberOfElements(), PiiMaskingUtil.maskCustomerId(customerId), alerts.getTotalElements(), alerts.getTotalPages());

        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/recent/paginated")
    @Operation(
            summary = "Get recent fraud alerts (paginated)",
            description = "Retrieve recent fraud alerts with pagination support to avoid large responses",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Paginated alerts retrieved successfully",
                            content = @Content(schema = @Schema(implementation = Page.class))
                    )
            }
    )
    public ResponseEntity<Page<FraudAlertResponse>> getRecentAlertsPaginated(
            @Parameter(description = "Number of hours to look back (1-720)", example = "24")
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Retrieving paginated fraud alerts from the last {} hours (page: {}, size: {})",
                hours, pageable.getPageNumber(), pageable.getPageSize());
        Page<FraudAlertResponse> alerts = fraudDetectionService.getRecentAlertsPaginated(hours, pageable);
        log.info("Found {} recent alerts (total: {}, pages: {})",
                alerts.getNumberOfElements(), alerts.getTotalElements(), alerts.getTotalPages());

        return ResponseEntity.ok(alerts);
    }
}
