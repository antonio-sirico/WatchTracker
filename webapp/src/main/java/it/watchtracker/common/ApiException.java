package it.watchtracker.common;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Errore applicativo con stato HTTP. Può portare messaggi per singolo campo, come previsto
 * dall'eccezione comune EC3 (il client li mostra accanto al campo interessato).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Collections.emptyMap());
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields == null ? Collections.emptyMap() : Map.copyOf(fields);
    }

    public static ApiException badRequest(String message, String field, String fieldMessage) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, fieldMessage));
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
