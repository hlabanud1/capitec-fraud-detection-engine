package za.co.capitec.fraud.util;

/**
 * Utility for masking Personally Identifiable Information (PII) in logs
 * to comply with POPIA (South Africa) and GDPR (EU) regulations.
 */
public final class PiiMaskingUtil {

    private PiiMaskingUtil() {
        // Prevent instantiation
    }

    /**
     * Masks customer ID for POPIA/GDPR compliance in logs.
     * Shows first 3 and last 3 characters only.
     *
     * @param customerId the customer ID to mask
     * @return masked customer ID (e.g., "CUST123456789" -> "CUS******789")
     */
    public static String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() <= 6) {
            return "***";
        }
        String prefix = customerId.substring(0, 3);
        String suffix = customerId.substring(customerId.length() - 3);
        return prefix + "******" + suffix;
    }

}
