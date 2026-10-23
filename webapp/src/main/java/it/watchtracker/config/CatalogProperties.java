package it.watchtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Proprietà con prefisso "watchtracker". */
@ConfigurationProperties(prefix = "watchtracker")
public record CatalogProperties(Tmdb tmdb, Jikan jikan) {

    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank() && !"chiaveapi".equals(apiKey);
        }
    }

    public record Jikan(String baseUrl) {}
}
