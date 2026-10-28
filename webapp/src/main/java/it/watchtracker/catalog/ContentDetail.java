package it.watchtracker.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/** Scheda completa di un contenuto (RF 0.4, RF 3.2, RF 3.7). */
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
        /** Durata del film oppure durata media di un episodio, in minuti. */
        Integer runtimeMinutes,
        Integer seasonCount,
        Integer episodeCount,
        List<Season> seasons,
        /** Stato grezzo riportato dal catalogo (es. "Ended", "Currently Airing"). */
        String status,
        Continuation continuation) {

    /** Una stagione con tutti gli episodi previsti dal catalogo (RF 3.2). */
    public record Season(int number, String name, int episodeCount, List<Episode> episodes) {}

    public record Episode(int number, String name, LocalDate airDate, Integer runtimeMinutes) {}

    @JsonProperty("sourceName")
    public String sourceName() {
        return source.label();
    }

    /** Numero totale di episodi effettivamente elencati nelle stagioni. */
    public int listedEpisodes() {
        return seasons.stream().mapToInt(s -> s.episodes().size()).sum();
    }
}
