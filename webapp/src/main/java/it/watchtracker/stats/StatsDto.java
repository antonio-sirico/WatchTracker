package it.watchtracker.stats;

import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Dashboard personale (RF 3.10–3.15 / UC-19). I valori non calcolabili sono null («non disponibile»). */
public record StatsDto(
        int libraryCount,
        int completedCount,
        /** RF 3.11: tempo totale di visione in minuti e come testo («2 giorni, 5 ore e 1 minuto»). */
        long totalMinutes,
        String watchTimeLabel,
        /** UC-19 E2: contenuti esclusi dal tempo totale perché il catalogo non ne indica la durata. */
        int withoutRuntime,
        /** RF 3.12: distribuzione dei contenuti completati per genere, in ordine decrescente. */
        List<GenreCount> genres,
        List<String> favoriteGenres,
        /** RF 3.13: titoli completati mese per mese (chiave "AAAA-MM"), in ordine cronologico. */
        List<MonthCount> completedByMonth,
        /** RF 3.14: rating medio complessivo e per tipologia, a una cifra decimale. */
        Double averageRating,
        Map<ContentType, Double> averageRatingByType,
        /** RF 3.15: serie in corso in attesa di nuovi episodi, per data crescente, date ignote in coda. */
        List<WaitingSeries> waiting) {

    public record GenreCount(String genre, int count) {}

    public record MonthCount(String month, int count) {}

    public record WaitingSeries(Long entryId, String title, ContentType type, String posterUrl,
                                Continuation.Status status, LocalDate nextDate, String label,
                                int watchedEpisodes, Integer episodeCount) {}
}
