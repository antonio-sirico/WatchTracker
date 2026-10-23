package it.watchtracker.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import it.watchtracker.config.CatalogProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Client TMDB/Jikan contro un server HTTP locale finto. */
class CatalogClientsTest {

    private static HttpServer server;
    private static CatalogService service;

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
        server.createContext("/tmdb/tv/37854", ex -> json(ex, """
                {"id":37854,"name":"One Piece","original_name":"ONE PIECE","first_air_date":"1999-10-20","original_language":"ja",
                 "origin_country":["JP"],"genres":[{"id":16,"name":"Animazione"}],"episode_run_time":[24],"number_of_seasons":22,
                 "number_of_episodes":1100,"status":"Returning Series","overview":"...",
                 "seasons":[{"season_number":0,"name":"Speciali","episode_count":40},{"season_number":1,"name":"East Blue","episode_count":61}]}
                """));
        server.createContext("/jikan/anime", ex -> {
            if (ex.getRequestURI().getPath().equals("/jikan/anime/21")) {
                json(ex, """
                        {"data":{"mal_id":21,"title":"One Piece","title_english":"One Piece","episodes":null,"status":"Currently Airing",
                         "duration":"24 min","year":1999,"synopsis":"Gol D. Roger...","images":{"jpg":{"large_image_url":"https://cdn/op.jpg"}},
                         "genres":[{"name":"Action"},{"name":"Adventure"}],"themes":[],"demographics":[{"name":"Shounen"}]}}
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
        service = new CatalogService(new TmdbClient(RestClient.builder(), props), new JikanClient(RestClient.builder(), props));
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
    void tmdbDetailExposesSeasonsWithoutSpecials() {
        ContentDetail d = service.detail(ContentSource.TMDB_TV, "37854");

        assertThat(d.type()).isEqualTo(ContentType.ANIME);
        assertThat(d.runtimeMinutes()).isEqualTo(24);
        assertThat(d.seasonCount()).isEqualTo(22);
        assertThat(d.seasons()).extracting(ContentDetail.Season::number).containsExactly(1);
        assertThat(d.genres()).containsExactly("Animazione");
    }

    @Test
    void jikanDetailIsParsed() {
        ContentDetail d = service.detail(ContentSource.MAL, "21");

        assertThat(d.title()).isEqualTo("One Piece");
        assertThat(d.year()).isEqualTo(1999);
        assertThat(d.runtimeMinutes()).isEqualTo(24);
        assertThat(d.episodeCount()).isNull();
        assertThat(d.seasons()).isEmpty();
        assertThat(d.genres()).containsExactly("Action", "Adventure", "Shounen");
        assertThat(d.posterUrl()).isEqualTo("https://cdn/op.jpg");
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
