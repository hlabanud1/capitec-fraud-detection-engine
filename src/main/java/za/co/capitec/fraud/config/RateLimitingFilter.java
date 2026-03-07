package za.co.capitec.fraud.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import za.co.capitec.fraud.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter to prevent DDoS attacks on transaction endpoint.
 * Uses sliding window algorithm with in-memory storage.
 */
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_SIZE_MS = 60_000; // 1 minute

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final ObjectMapper objectMapper;

    // Map of IP address -> list of request timestamps
    private final Map<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only rate limit transaction endpoint
        String path = request.getRequestURI();
        String expectedPath = contextPath + "/api/v1/transactions";
        if (!path.equals(expectedPath) || !request.getMethod().equals("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIP(request);
        long now = System.currentTimeMillis();

        RequestWindow window = requestWindows.computeIfAbsent(clientIp, k -> new RequestWindow());

        synchronized (window) {
            // Remove old requests outside the window
            window.removeOldRequests(now - WINDOW_SIZE_MS);

            if (window.getRequestCount() >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                ErrorResponse errorResponse = ErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Rate Limit Exceeded",
                        "Maximum %d requests per minute allowed. Please retry after 60 seconds.".formatted(MAX_REQUESTS_PER_MINUTE),
                        null
                );

                try {
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                } catch (Exception e) {
                    log.error("Failed to serialize rate limit error response", e);
                    // Fallback JSON matching ErrorResponse structure
                    response.getWriter().write("{\"status\":429,\"error\":\"Rate Limit Exceeded\",\"message\":\"Too many requests\",\"path\":null}");
                }
                return;
            }

            window.addRequest(now);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Sliding window tracker for a single client.
     */
    private static class RequestWindow {
        private final ConcurrentHashMap<Long, Boolean> requests = new ConcurrentHashMap<>();

        public void addRequest(long timestamp) {
            requests.put(timestamp, Boolean.TRUE);
        }

        public void removeOldRequests(long cutoffTime) {
            requests.keySet().removeIf(timestamp -> timestamp < cutoffTime);
        }

        public int getRequestCount() {
            return requests.size();
        }
    }
}
