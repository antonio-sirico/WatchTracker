package it.watchtracker.catalog;

import static it.watchtracker.catalog.JsonSupport.integer;
import static it.watchtracker.catalog.JsonSupport.text;
import static it.watchtracker.catalog.JsonSupport.yearOf;

import com.fasterxml.jackson.databind.JsonNode;
import it.watchtracker.config.CatalogProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Client TMDB: film e serie TV (RF 2.1). */
@Component
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);
    private static final int GENRE_ANIMATION = 16;

    private final RestClient http;
    private final CatalogProperties.Tmdb cfg;
    private final AtomicReference<Map<Integer, String>> genreCache = new AtomicReference<>();

    public TmdbClient(RestClient.Builder builder, CatalogProperties props) {
        this.cfg = props.tmdb();
        this.http = builder.baseUrl(cfg.baseUrl()).build();
    }

    public boolean isConfigured() {
        return cfg.configured();
    }

    public List<ContentSummary> search(String query) {
        JsonNode root = http.get()
                .uri(b -> b.path("/search/multi")
                        .queryParam("api_key", cfg.apiKey())
                        .queryParam("language", "it-IT")
                        .queryParam("include_adult", "false")
                        .queryParam("query", "{q}")
                        .build(query))
                .retrieve()
                .body(JsonNode.class);

        Map<Integer, String> genreNames = genreNames();
        List<ContentSummary> out = new ArrayList<>();
        for (JsonNode n : root.path("results")) {
            String mediaType = n.path("media_type").asText();
            if (mediaType.equals("movie") || mediaType.equals("tv")) {
                out.add(toSummary(n, mediaType.equals("movie"), genreNames));
            }
        }
        return out;
    }

    public ContentDetail detail(ContentSource source, String id) {
        boolean movie = source == ContentSource.TMDB_MOVIE;
        String kind = movie ? "movie" : "tv";
        JsonNode n = http.get()
                .uri(b -> b.path("/" + kind + "/{id}")
                        .queryParam("api_key", cfg.apiKey())
                        .queryParam("language", "it-IT")
                        .build(id))
                .retrieve()
                .body(JsonNode.class);

        List<Integer> genreIds = new ArrayList<>();
        List<String> genres = new ArrayList<>();
        for (JsonNode g : n.path("genres")) {
            genreIds.add(g.path("id").asInt());
            genres.add(g.path("name").asText());
        }
        boolean japanese = "ja".equals(text(n, "original_language"));
        for (JsonNode c : n.path("origin_country")) {
            japanese |= "JP".equals(c.asText());
        }
        for (JsonNode c : n.path("production_countries")) {
            japanese |= "JP".equals(c.path("iso_3166_1").asText());
        }
        ContentType type = classify(movie, genreIds.contains(GENRE_ANIMATION), japanese);

        Integer runtime = movie ? integer(n, "runtime") : firstInt(n.path("episode_run_time"));
        List<ContentDetail.Season> seasons = new ArrayList<>();
        for (JsonNode s : n.path("seasons")) {
            int number = s.path("season_number").asInt();
            if (number > 0) { // la "stagione 0" di TMDB raccoglie gli speciali
                seasons.add(new ContentDetail.Season(number, text(s, "name"), s.path("episode_count").asInt()));
            }
        }

        return new ContentDetail(
                source,
                id,
                type,
                text(n, movie ? "title" : "name"),
                text(n, movie ? "original_title" : "original_name"),
                yearOf(text(n, movie ? "release_date" : "first_air_date")),
                poster(n),
                text(n, "overview"),
                genres,
                runtime,
                movie ? null : integer(n, "number_of_seasons"),
                movie ? null : integer(n, "number_of_episodes"),
                seasons,
                text(n, "status"));
    }

    private ContentSummary toSummary(JsonNode n, boolean movie, Map<Integer, String> genreNames) {
        List<Integer> genreIds = new ArrayList<>();
        n.path("genre_ids").forEach(g -> genreIds.add(g.asInt()));
        boolean japanese = "ja".equals(text(n, "original_language"));
        for (JsonNode c : n.path("origin_country")) {
            japanese |= "JP".equals(c.asText());
        }
        String title = text(n, movie ? "title" : "name");
        String original = text(n, movie ? "original_title" : "original_name");
        return new ContentSummary(
                movie ? ContentSource.TMDB_MOVIE : ContentSource.TMDB_TV,
                n.path("id").asText(),
                classify(movie, genreIds.contains(GENRE_ANIMATION), japanese),
                title,
                original,
                yearOf(text(n, movie ? "release_date" : "first_air_date")),
                poster(n),
                text(n, "overview"),
                genreIds.stream().map(genreNames::get).filter(Objects::nonNull).toList(),
                original == null ? List.of() : List.of(original));
    }

    /** RF 2.2 e RF 2.3: un titolo TMDB con genere Animazione e origine giapponese è un anime. */
    private static ContentType classify(boolean movie, boolean animation, boolean japanese) {
        if (animation && japanese) {
            return ContentType.ANIME;
        }
        return movie ? ContentType.FILM : ContentType.SERIE_TV;
    }

    private String poster(JsonNode n) {
        String path = text(n, "poster_path");
        return path == null ? null : cfg.imageBaseUrl() + path;
    }

    private static Integer firstInt(JsonNode array) {
        for (JsonNode v : array) {
            if (v.asInt() > 0) {
                return v.asInt();
            }
        }
        return null;
    }

    /** Mappa id -> nome dei generi, caricata una volta sola. */
    private Map<Integer, String> genreNames() {
        Map<Integer, String> cached = genreCache.get();
        if (cached != null) {
            return cached;
        }
        Map<Integer, String> map = new HashMap<>();
        try {
            for (String kind : List.of("movie", "tv")) {
                JsonNode root = http.get()
                        .uri(b -> b.path("/genre/" + kind + "/list")
                                .queryParam("api_key", cfg.apiKey())
                                .queryParam("language", "it-IT")
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
                for (JsonNode g : root.path("genres")) {
                    map.put(g.path("id").asInt(), g.path("name").asText());
                }
            }
            genreCache.set(Map.copyOf(map));
        } catch (RuntimeException e) {
            log.warn("Impossibile caricare i nomi dei generi da TMDB: {}", e.getMessage());
        }
        return map;
    }
}
