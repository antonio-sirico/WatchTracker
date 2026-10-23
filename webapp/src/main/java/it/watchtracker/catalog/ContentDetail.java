package it.watchtracker.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Scheda completa di un contenuto (RF 0.4, RF 3.2). */
public record ContentDetail(
        ContentSource source,
        String externalId,
        ContentType type,
        String title,
        String originalTitle,
        Integer year,
        String posterUrl,
        String overview,
        List<String> genres,
        Integer runtimeMinutes,
        Integer seasonCount,
        Integer episodeCount,
        List<Season> seasons,
        String status) {

    public record Season(int number, String name, int episodeCount) {}

    @JsonProperty("sourceName")
    public String sourceName() {
        return source.label();
    }
}
