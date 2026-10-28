package it.watchtracker.stats;

import static org.assertj.core.api.Assertions.assertThat;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import it.watchtracker.library.EpisodeRef;
import it.watchtracker.library.LibraryEntry;
import it.watchtracker.library.WatchStatus;
import it.watchtracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    private final StatsService service = new StatsService(null);
    private final User user = new User("anna", "anna@example.com", "hash");

    @Test
    void rf311_watchTimeIsFormattedInDaysHoursAndMinutes() {
        assertThat(StatsService.formatMinutes(2 * 24 * 60 + 5 * 60 + 1)).isEqualTo("2 giorni, 5 ore e 1 minuto");
        assertThat(StatsService.formatMinutes(24 * 60 + 60)).isEqualTo("1 giorno e 1 ora");
        assertThat(StatsService.formatMinutes(45)).isEqualTo("45 minuti");
        assertThat(StatsService.formatMinutes(0)).isEqualTo("0 minuti");
        assertThat(StatsService.formatMinutes(3 * 60)).isEqualTo("3 ore");
    }

    @Test
    void rf314_averageRoundedToOneDecimalOrNotAvailable() {
        assertThat(StatsService.average(List.of(5, 4, 4))).isEqualTo(4.3);
        assertThat(StatsService.average(List.of())).isNull();
    }

    @Test
    void dashboardIsComputedFromLibraryAndProgress() {
        LibraryEntry film = entry(ContentSource.TMDB_MOVIE, "550", ContentType.FILM, "Fight Club",
                List.of("Dramma"), 139, null);
        film.setStatus(WatchStatus.COMPLETATO);
        film.setRating(5, Instant.now());

        LibraryEntry series = entry(ContentSource.TMDB_TV, "66732", ContentType.SERIE_TV, "Stranger Things",
                List.of("Dramma", "Fantascienza"), 50, 5);
        series.markWatched(new EpisodeRef(1, 1), Instant.now());
        series.markWatched(new EpisodeRef(1, 2), Instant.now());
        series.setStatus(WatchStatus.IN_CORSO);
        series.applySnapshot(null, null, null, null, List.of("Dramma", "Fantascienza"), 50, 2, 5,
                Continuation.Status.PROSSIMAMENTE, null);

        LibraryEntry anime = entry(ContentSource.MAL, "21", ContentType.ANIME, "One Piece",
                List.of("Action"), 24, 4);
        for (int i = 1; i <= 4; i++) {
            anime.markWatched(new EpisodeRef(1, i), Instant.now());
        }
        anime.setStatus(WatchStatus.COMPLETATO);
        anime.setRating(4, Instant.now());

        LibraryEntry noRuntime = entry(ContentSource.MAL, "99", ContentType.ANIME, "Senza durata",
                List.of("Action", "Dramma"), null, 1);
        noRuntime.markWatched(new EpisodeRef(1, 1), Instant.now());
        noRuntime.setStatus(WatchStatus.COMPLETATO);

        LibraryEntry waitingDated = entry(ContentSource.MAL, "22", ContentType.ANIME, "In attesa con data",
                List.of(), 24, 10);
        waitingDated.setStatus(WatchStatus.IN_CORSO);
        waitingDated.applySnapshot(null, null, null, null, List.of(), 24, 1, 10,
                Continuation.Status.DATA, LocalDate.of(2026, 11, 1));

        StatsDto stats = service.compute(List.of(film, series, anime, noRuntime, waitingDated));

        // RF 3.11: 139 (film) + 2x50 (serie) + 4x24 (anime) = 335 minuti; il contenuto senza durata è escluso
        assertThat(stats.totalMinutes()).isEqualTo(335);
        assertThat(stats.watchTimeLabel()).isEqualTo("5 ore e 35 minuti");
        assertThat(stats.withoutRuntime()).isEqualTo(1);

        // RF 3.12: solo i completati; Action (anime + senza durata) e Dramma (film + senza durata) a pari merito
        assertThat(stats.completedCount()).isEqualTo(3);
        assertThat(stats.genres()).extracting(StatsDto.GenreCount::genre).containsExactly("Action", "Dramma");
        assertThat(stats.favoriteGenres()).containsExactly("Action", "Dramma");

        // RF 3.13: tutti completati nel mese corrente
        assertThat(stats.completedByMonth()).hasSize(1);
        assertThat(stats.completedByMonth().get(0).count()).isEqualTo(3);

        // RF 3.14
        assertThat(stats.averageRating()).isEqualTo(4.5);
        assertThat(stats.averageRatingByType().get(ContentType.FILM)).isEqualTo(5.0);
        assertThat(stats.averageRatingByType().get(ContentType.ANIME)).isEqualTo(4.0);
        assertThat(stats.averageRatingByType().get(ContentType.SERIE_TV)).isNull();

        // RF 3.15: prima la serie con data, poi quella "Prossimamente"
        assertThat(stats.waiting()).extracting(StatsDto.WaitingSeries::title)
                .containsExactly("In attesa con data", "Stranger Things");
        assertThat(stats.waiting().get(1).label()).isEqualTo("Prossimamente");
    }

    private LibraryEntry entry(ContentSource source, String id, ContentType type, String title,
                               List<String> genres, Integer runtime, Integer episodes) {
        LibraryEntry e = new LibraryEntry(user, source, id, type, title);
        e.applySnapshot(title, type, 2000, null, genres, runtime, episodes == null ? null : 1, episodes, null, null);
        return e;
    }
}
