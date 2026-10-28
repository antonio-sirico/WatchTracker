package it.watchtracker.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.library.LibraryEntryDto;
import it.watchtracker.stats.StatsDto;
import java.time.Instant;
import java.util.List;

/** Profilo pubblico in sola lettura (RF 4.2, RF 4.3 / UC-08): solo le sezioni rese visibili dal proprietario. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProfileDto(
        String nickname,
        Instant memberSince,
        boolean showLibrary,
        boolean showStats,
        boolean showReviews,
        List<LibraryEntryDto> library,
        StatsDto stats,
        List<ReviewDto> reviews) {

    public static final String NOT_AVAILABLE = "Questo profilo non è disponibile";

    public record ReviewDto(Long entryId, ContentSource source, String externalId, ContentType type,
                            String title, Integer year, String posterUrl, Integer rating, String review,
                            Instant reviewUpdatedAt) {}
}
