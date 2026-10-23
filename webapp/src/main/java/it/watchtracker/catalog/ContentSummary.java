package it.watchtracker.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Elemento di un risultato di ricerca (RF 0.3). */
public record ContentSummary(
        ContentSource source,
        String externalId,
        ContentType type,
        String title,
        String originalTitle,
        Integer year,
        String posterUrl,
        String overview,
        List<String> genres,
        @JsonIgnore List<String> alternativeTitles) {

    @JsonProperty("sourceName")
    public String sourceName() {
        return source.label();
    }
}
