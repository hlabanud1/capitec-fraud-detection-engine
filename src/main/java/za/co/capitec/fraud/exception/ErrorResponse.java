package za.co.capitec.fraud.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors
) {

    public static ErrorResponse of(int status, String error, String message, Map<String, String> validationErrors) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, validationErrors);
    }
}
