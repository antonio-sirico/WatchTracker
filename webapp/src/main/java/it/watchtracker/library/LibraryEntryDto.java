package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import java.time.Instant;

public record LibraryEntryDto(
        Long id,
        ContentSource source,
        String externalId,
        ContentType type,
        String title,
        Integer year,
        String posterUrl,
        WatchStatus status,
        Instant addedAt,
        Instant updatedAt) {

    public static LibraryEntryDto from(LibraryEntry e) {
        return new LibraryEntryDto(e.getId(), e.getSource(), e.getExternalId(), e.getType(), e.getTitle(),
                e.getYear(), e.getPosterUrl(), e.getStatus(), e.getAddedAt(), e.getUpdatedAt());
    }
}
