package it.watchtracker.catalog;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Chiamata verso un catalogo esterno con un solo nuovo tentativo in caso di errore transitorio
 * (RNF-R13). Il timeout di 5 secondi (RNF-R12) è configurato in {@code HttpClientConfig}.
 */
final class CatalogCall {

    private static final Logger log = LoggerFactory.getLogger(CatalogCall.class);

    private CatalogCall() {}

    static <T> T withRetry(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            // 4xx: la richiesta è sbagliata o il contenuto non esiste, ritentare non serve
            throw e;
        } catch (RestClientException e) {
            log.warn("{}: errore transitorio ({}), nuovo tentativo", what, e.getMessage());
            return call.get();
        }
    }
}
