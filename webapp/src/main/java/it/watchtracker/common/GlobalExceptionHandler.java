package it.watchtracker.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** UC-07 E2: il catalogo di origine non risponde. */
    public static final String CATALOG_DOWN =
            "Le informazioni sul contenuto non sono al momento disponibili: riprova più tardi";

    public record ApiError(int status, String message, Map<String, String> fields) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return build(e.getStatus(), e.getMessage(), e.getFields().isEmpty() ? null : e.getFields());
    }

    /** EC3: dati non validi, con il messaggio accanto a ciascun campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Dati non validi", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Richiesta non valida", null);
    }

    /** UC-21: il file di backup supera la dimensione massima. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Il file supera la dimensione massima di 20 MB", null);
    }

    /** Il catalogo esterno non conosce l'identificativo richiesto. */
    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<ApiError> handleNotFound(HttpClientErrorException.NotFound e) {
        return build(HttpStatus.NOT_FOUND, "Contenuto non trovato nel catalogo esterno", null);
    }

    /** EC2 / UC-07 E2: catalogo esterno non raggiungibile. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleExternal(RestClientException e) {
        log.warn("Errore dal catalogo esterno: {}", e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, CATALOG_DOWN, null);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fields));
    }
}
