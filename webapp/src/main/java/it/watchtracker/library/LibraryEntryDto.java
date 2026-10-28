package it.watchtracker.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Un contenuto della libreria come viene restituito al client (RF 2.8). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LibraryEntryDto(
        Long id,
        ContentSource source,
        String externalId,
        ContentType type,
        String title,
        Integer year,
        String posterUrl,
        List<String> genres,
        Integer runtimeMinutes,
        Integer seasonCount,
        Integer episodeCount,
        WatchStatus status,
        /** Numero di episodi visti (per serie TV e anime). */
        int watchedEpisodes,
        ContinuationDto continuation,
        Integer rating,
        String review,
        Instant reviewUpdatedAt,
        Instant addedAt,
        Instant updatedAt,
        Instant completedAt,
        /** Elenco degli episodi visti; presente solo nelle risposte relative a un singolo contenuto. */
        List<EpisodeRef> watched) {

    public record ContinuationDto(Continuation.Status status, LocalDate nextDate, String label) {
        static ContinuationDto from(Continuation c) {
            return new ContinuationDto(c.status(), c.nextDate(), c.label());
        }
    }

    public static LibraryEntryDto summary(LibraryEntry e, int watchedEpisodes) {
        return build(e, watchedEpisodes, null);
    }

    public static LibraryEntryDto full(LibraryEntry e) {
        List<EpisodeRef> refs = e.getWatched().stream().map(EpisodeProgress::ref).sorted().toList();
        return build(e, refs.size(), refs);
    }

    private static LibraryEntryDto build(LibraryEntry e, int watchedEpisodes, List<EpisodeRef> refs) {
        return new LibraryEntryDto(e.getId(), e.getSource(), e.getExternalId(), e.getType(), e.getTitle(),
                e.getYear(), e.getPosterUrl(), e.genreList(), e.getRuntimeMinutes(), e.getSeasonCount(),
                e.getEpisodeCount(), e.getStatus(), watchedEpisodes,
                e.continuation().map(ContinuationDto::from).orElse(null),
                e.getRating(), e.getReview(), e.getReviewUpdatedAt(),
                e.getAddedAt(), e.getUpdatedAt(), e.getCompletedAt(), refs);
    }
}
