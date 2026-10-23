package it.watchtracker.common;

import org.springframework.http.HttpStatus;

/** Errore applicativo con stato HTTP. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
