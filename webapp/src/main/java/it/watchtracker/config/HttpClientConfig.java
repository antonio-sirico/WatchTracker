package it.watchtracker.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** RNF-R12: le richieste ai cataloghi esterni sono abbandonate dopo 5 secondi senza risposta. */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer externalCatalogTimeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(5));
            builder.requestFactory(factory);
        };
    }
}
