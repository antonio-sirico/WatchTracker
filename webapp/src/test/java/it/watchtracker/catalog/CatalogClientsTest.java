package it.watchtracker.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import it.watchtracker.config.CatalogProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Client TMDB/Jikan contro un server HTTP locale finto. */
class CatalogClientsTest {

    /** Mercoledì 28 ottobre 2026. */
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);

    private static HttpServer server;
    private static CatalogService service;
    private static final AtomicInteger flakyCalls = new AtomicInteger();

    @BeforeAll
    static void startFakeCatalogs() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tmdb/search/multi", ex -> json(ex, """
                {"results":[
                  {"media_type":"tv","id":37854,"name":"One Piece","original_name":"ONE PIECE","first_air_date":"1999-10-20",
                   "genre_ids":[16,10759],"origin_country":["JP"],"original_language":"ja","poster_path":"/op.jpg","overview":"Rufy..."},
                  {"media_type":"tv","id":111110,"name":"One Piece","original_name":"One Piece","first_air_date":"2023-08-31",
                   "genre_ids":[10759,18],"origin_country":["US"],"original_language":"en","poster_path":null,"overview":"Live action"},
                  {"media_type":"movie","id":550,"title":"Fight Club","original_title":"Fight Club","release_date":"1999-10-15",
                   "genre_ids":[18],"original_language":"en","poster_path":"/fc.jpg","overview":"..."},
                  {"media_type":"person","id":1,"name":"Qualcuno"}
                ]}
                """));
        server.createContext("/tmdb/genre/movie/list", ex -> json(ex, """
                {"genres":[{"id":18,"name":"Dramma"},{"id":16,"name":"Animazione"}]}"""));
        server.createContext("/tmdb/genre/tv/list", ex -> json(ex, """
                {"genres":[{"id":10759,"name":"Action & Adventure"},{"id":16,"name":"Animazione"},{"id":18,"name":"Dramma"}]}"""));
        // Serie con due stagioni: la scheda e, con append_to_response, gli episodi delle stagioni
        server.createContext("/tmdb/tv/37854", ex -> json(ex, """
                {"id":37854,"name":"One Piece","original_name":"ONE PIECE","first_air_date":"1999-10-20","original_language":"ja",
                 "origin_country":["JP"],"genres":[{"id":16,"name":"Animazione"}],"episode_run_time":[24],"number_of_seasons":2,
                 "number_of_episodes":5,"status":"Returning Series","in_production":true,"overview":"...",
                 "next_episode_to_air":{"air_date":"2026-11-01","episode_number":6,"season_number":2},
                 "seasons":[{"season_number":0,"name":"Speciali","episode_count":40},
                            {"season_number":1,"name":"East Blue","episode_count":3},
                            {"season_number":2,"name":"Alabasta","episode_count":2}],
                 "season/1":{"episodes":[{"episode_number":1,"name":"Io sono Rufy","air_date":"1999-10-20","runtime":24},
                                         {"episode_number":2,"name":"Zoro","air_date":"1999-11-17","runtime":24},
                                         {"episode_number":3,"name":"Nami","air_date":"1999-11-24","runtime":25}]},
                 "season/2":{"episodes":[{"episode_number":1,"name":"Alabasta 1","air_date":"2000-01-01","runtime":24},
                                         {"episode_number":2,"name":"Alabasta 2","air_date":"2000-01-08","runtime":24}]}}
                """));
        // Film: il primo tentativo fallisce con 500, il secondo risponde (RNF-R13)
        server.createContext("/tmdb/movie/550", ex -> {
            if (flakyCalls.getAndIncrement() == 0) {
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(500, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            json(ex, """
                    {"id":550,"title":"Fight Club","original_title":"Fight Club","release_date":"1999-10-15","runtime":139,
                     "original_language":"en","genres":[{"id":18,"name":"Dramma"}],"status":"Released","overview":"..."}
                    """);
        });
        server.createContext("/jikan/anime", ex -> {
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();
            if (path.equals("/jikan/anime/21/episodes")) {
                if (query != null && query.contains("page=2")) {
                    json(ex, """
                            {"pagination":{"last_visible_page":2,"has_next_page":false},
                             "data":[{"mal_id":101,"title":"Ep 101"},{"mal_id":102,"title":"Ep 102"},{"mal_id":103,"title":"Ep 103"}]}
                            """);
                } else {
                    json(ex, """
                            {"pagination":{"last_visible_page":2,"has_next_page":true},
                             "data":[{"mal_id":1,"title":"I'm Luffy!"},{"mal_id":2,"title":"Enter Zoro"}]}
                            """);
                }
            } else if (path.equals("/jikan/anime/21")) {
                json(ex, """
                        {"data":{"mal_id":21,"title":"One Piece","title_english":"One Piece","episodes":null,"status":"Currently Airing",
                         "duration":"24 min","year":1999,"synopsis":"Gol D. Roger...","images":{"jpg":{"large_image_url":"https://cdn/op.jpg"}},
                         "broadcast":{"day":"Sundays","time":"23:00","timezone":"Asia/Tokyo"},
                         "genres":[{"name":"Action"},{"name":"Adventure"}],"themes":[],"demographics":[{"name":"Shounen"}]}}
                        """);
            } else if (path.equals("/jikan/anime/5114/episodes")) {
                json(ex, """
                        {"pagination":{"last_visible_page":1,"has_next_page":false},
                         "data":[{"mal_id":1,"title":"Fullmetal Alchemist"},{"mal_id":2,"title":"The First Day"}]}
                        """);
            } else if (path.equals("/jikan/anime/5114")) {
                json(ex, """
                        {"data":{"mal_id":5114,"title":"Fullmetal Alchemist: Brotherhood","episodes":2,"status":"Finished Airing",
                         "duration":"24 min per ep","year":2009,"images":{"jpg":{"image_url":"https://cdn/fma.jpg"}},"genres":[]}}
                        """);
            } else {
                json(ex, """
                        {"data":[{"mal_id":21,"title":"One Piece","title_english":"One Piece","year":1999,
                          "titles":[{"type":"Default","title":"One Piece"},{"type":"Japanese","title":"ONE PIECE"}],
                          "images":{"jpg":{"large_image_url":"https://cdn/op.jpg"}},"synopsis":"...","genres":[{"name":"Action"}]}]}
                        """);
            }
        });
        server.start();

        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        CatalogProperties props = new CatalogProperties(
                new CatalogProperties.Tmdb("chiave-di-test", base + "/tmdb", "https://image.tmdb.org/t/p/w342"),
                new CatalogProperties.Jikan(base + "/jikan"));
        service = new CatalogService(new TmdbClient(RestClient.builder(), props),
                new JikanClient(RestClient.builder(), props, TODAY));
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void unifiedSearchClassifiesAndDeduplicates() {
        SearchResponse response = service.search("one piece");

        assertThat(response.warnings()).isEmpty();
        assertThat(response.results()).hasSize(3);
        assertThat(response.results()).extracting(ContentSummary::title)
                .containsExactly("One Piece", "Fight Club", "One Piece");
        assertThat(response.results()).extracting(ContentSummary::type)
                .containsExactly(ContentType.SERIE_TV, ContentType.FILM, ContentType.ANIME);
        assertThat(response.results()).extracting(ContentSummary::source)
                .containsExactly(ContentSource.TMDB_TV, ContentSource.TMDB_MOVIE, ContentSource.MAL);

        ContentSummary fightClub = response.results().get(1);
        assertThat(fightClub.year()).isEqualTo(1999);
        assertThat(fightClub.genres()).containsExactly("Dramma");
        assertThat(fightClub.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w342/fc.jpg");
    }

    @Test
    void rf32_tmdbDetailListsAllSeasonsAndEpisodesWithoutSpecials() {
        ContentDetail d = service.detail(ContentSource.TMDB_TV, "37854");

        assertThat(d.type()).isEqualTo(ContentType.ANIME);
        assertThat(d.runtimeMinutes()).isEqualTo(24);
        assertThat(d.seasonCount()).isEqualTo(2);
        assertThat(d.episodeCount()).isEqualTo(5);
        assertThat(d.seasons()).extracting(ContentDetail.Season::number).containsExactly(1, 2);
        assertThat(d.seasons().get(0).episodes()).extracting(ContentDetail.Episode::name)
                .containsExactly("Io sono Rufy", "Zoro", "Nami");
        assertThat(d.seasons().get(0).episodes().get(0).airDate()).isEqualTo(LocalDate.of(1999, 10, 20));
        assertThat(d.listedEpisodes()).isEqualTo(5);
        assertThat(d.genres()).containsExactly("Animazione");
    }

    @Test
    void rf37_tmdbContinuationUsesNextEpisodeDate() {
        ContentDetail d = service.detail(ContentSource.TMDB_TV, "37854");

        assertThat(d.continuation().status()).isEqualTo(Continuation.Status.DATA);
        assertThat(d.continuation().nextDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(d.continuation().label()).isEqualTo("Prossimo episodio: 2026-11-01");
    }

    @Test
    void rnfR13_transientErrorIsRetriedOnce() {
        flakyCalls.set(0);
        ContentDetail d = service.detail(ContentSource.TMDB_MOVIE, "550");

        assertThat(flakyCalls.get()).isEqualTo(2);
        assertThat(d.runtimeMinutes()).isEqualTo(139);
        assertThat(d.seasons()).isEmpty();
        assertThat(d.continuation()).isNull();
    }

    @Test
    void jikanDetailCountsEpisodesOfAnOngoingAnime() {
        ContentDetail d = service.detail(ContentSource.MAL, "21");

        assertThat(d.title()).isEqualTo("One Piece");
        assertThat(d.year()).isEqualTo(1999);
        assertThat(d.runtimeMinutes()).isEqualTo(24);
        // numero dichiarato assente: il totale si ricava dall'ultima pagina (100 + 3)
        assertThat(d.episodeCount()).isEqualTo(103);
        assertThat(d.seasons()).hasSize(1);
        assertThat(d.seasons().get(0).episodes()).hasSize(103);
        assertThat(d.seasons().get(0).episodes().get(0).name()).isEqualTo("I'm Luffy!");
        assertThat(d.seasons().get(0).episodes().get(2).name()).isNull();
        assertThat(d.genres()).containsExactly("Action", "Adventure", "Shounen");
        assertThat(d.posterUrl()).isEqualTo("https://cdn/op.jpg");
        // in onda la domenica: la prossima domenica dopo mercoledì 28/10/2026 è il 1/11/2026
        assertThat(d.continuation().status()).isEqualTo(Continuation.Status.DATA);
        assertThat(d.continuation().nextDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void jikanFinishedAnimeIsTerminata() {
        ContentDetail d = service.detail(ContentSource.MAL, "5114");

        assertThat(d.episodeCount()).isEqualTo(2);
        assertThat(d.seasons().get(0).episodes()).extracting(ContentDetail.Episode::name)
                .containsExactly("Fullmetal Alchemist", "The First Day");
        assertThat(d.continuation()).isEqualTo(Continuation.TERMINATA);
        assertThat(d.continuation().label()).isEqualTo("Terminata");
    }

    private static void json(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
