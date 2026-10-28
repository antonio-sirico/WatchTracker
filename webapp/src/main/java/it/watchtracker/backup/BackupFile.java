package it.watchtracker.backup;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import it.watchtracker.library.WatchStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Formato del backup (RF 4.7, RNF-IF3): un unico file JSON con la versione del formato e un
 * checksum SHA-256 del blocco {@code data}, con cui il sistema verifica compatibilità e integrità
 * prima dell'importazione (RF 4.8).
 */
public record BackupFile(
        String format,
        Integer version,
        Instant exportedAt,
        String exportedBy,
        Data data,
        String checksum) {

    public static final String FORMAT = "watchtracker-backup";
    public static final int VERSION = 1;

    public record Data(Settings settings, List<Entry> entries) {}

    /** RF 4.7: impostazioni dell'utente (visibilità del profilo). */
    public record Settings(boolean publicProfile, boolean showLibrary, boolean showStats, boolean showReviews) {}

    public record Entry(
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
            Continuation.Status continuation,
            LocalDate nextEpisodeDate,
            WatchStatus status,
            Integer rating,
            Instant ratedAt,
            String review,
            Instant reviewUpdatedAt,
            Instant addedAt,
            Instant updatedAt,
            Instant completedAt,
            List<WatchedEpisode> watched) {

        public List<WatchedEpisode> watchedOrEmpty() {
            return watched == null ? List.of() : watched;
        }
    }

    public record WatchedEpisode(int season, int episode, Instant watchedAt) {}

    /** UC-21 passo 4: riepilogo mostrato prima della conferma (RF 4.9). */
    public record Summary(String exportedBy, Instant exportedAt, int entries, int watchedEpisodes,
                          int ratings, int reviews, int alreadyPresent, boolean settingsOnly) {}

    /** UC-21 passo 7: esito dell'importazione (RF 4.10). */
    public record ImportResult(int imported, int merged, int watchedEpisodes, boolean settingsApplied) {}
}
