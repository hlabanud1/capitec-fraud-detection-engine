package za.co.capitec.fraud.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper utility for creating JSON details strings for fraud alerts.
 */
@Slf4j
public class FraudAlertDetailsHelper {

    private FraudAlertDetailsHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts an ObjectNode to a JSON string for fraud alert details.
     */
    public static String toJson(ObjectNode detailsNode, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(detailsNode);
        } catch (Exception e) {
            log.error("Error creating fraud alert details JSON", e);
            throw new IllegalStateException("Failed to create fraud alert details", e);
        }
    }
}
