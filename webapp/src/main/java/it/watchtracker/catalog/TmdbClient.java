package it.watchtracker.catalog;

import static it.watchtracker.catalog.JsonSupport.integer;
import static it.watchtracker.catalog.JsonSupport.localDate;
import static it.watchtracker.catalog.JsonSupport.text;
import static it.watchtracker.catalog.JsonSupport.yearOf;

import com.fasterxml.jackson.databind.JsonNode;
import it.watchtracker.config.CatalogProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Client TMDB: film e serie TV (RF 2.1). */
@Component
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);
    private static final int GENRE_ANIMATION = 16;
    /** TMDB accetta al massimo 20 sotto-richieste in append_to_response. */
    private static final int APPEND_LIMIT = 20;
    private static final Set<String> ENDED = Set.of("Ended", "Canceled");
    private static final Set<String> CONTINUING = Set.of("Returning Series", "In Production", "Planned", "Pilot");

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
        JsonNode root = CatalogCall.withRetry("TMDB search", () -> http.get()
                .uri(b -> b.path("/search/multi")
                        .queryParam("api_key", cfg.apiKey())
                        .queryParam("language", "it-IT")
                        .queryParam("include_adult", "false")
                        .queryParam("query", "{q}")
                        .build(query))
                .retrieve()
                .body(JsonNode.class));

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
        JsonNode n = fetch(kind, id, List.of());

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
        List<ContentDetail.Season> seasons = movie ? List.of() : seasons(id, n);
        if (!movie && runtime == null) {
            runtime = averageEpisodeRuntime(seasons);
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
                text(n, "status"),
                movie ? null : continuation(n));
    }

    /**
     * RF 3.2: tutte le stagioni con tutti gli episodi. Le stagioni vengono richieste insieme alla
     * scheda con append_to_response, a gruppi di 20; la "stagione 0" di TMDB raccoglie gli speciali
     * e viene esclusa.
     */
    private List<ContentDetail.Season> seasons(String id, JsonNode show) {
        List<Integer> numbers = new ArrayList<>();
        Map<Integer, JsonNode> headers = new HashMap<>();
        for (JsonNode s : show.path("seasons")) {
            int number = s.path("season_number").asInt();
            if (number > 0) {
                numbers.add(number);
                headers.put(number, s);
            }
        }
        Map<Integer, JsonNode> fetched = new HashMap<>();
        for (int from = 0; from < numbers.size(); from += APPEND_LIMIT) {
            List<Integer> batch = numbers.subList(from, Math.min(from + APPEND_LIMIT, numbers.size()));
            JsonNode node = fetch("tv", id, batch.stream().map(x -> "season/" + x).toList());
            for (int number : batch) {
                fetched.put(number, node.path("season/" + number));
            }
        }

        List<ContentDetail.Season> out = new ArrayList<>();
        for (int number : numbers) {
            JsonNode header = headers.get(number);
            List<ContentDetail.Episode> episodes = new ArrayList<>();
            for (JsonNode e : fetched.getOrDefault(number, header).path("episodes")) {
                episodes.add(new ContentDetail.Episode(
                        e.path("episode_number").asInt(),
                        text(e, "name"),
                        localDate(text(e, "air_date")),
                        integer(e, "runtime")));
            }
            int declared = header.path("episode_count").asInt();
            out.add(new ContentDetail.Season(number, text(header, "name"),
                    episodes.isEmpty() ? declared : episodes.size(), episodes));
        }
        return out;
    }

    /** RF 3.7: stato di prosecuzione di una serie TV ricavato dai campi di TMDB. */
    static Continuation continuation(JsonNode show) {
        LocalDate next = localDate(text(show.path("next_episode_to_air"), "air_date"));
        if (next != null) {
            return Continuation.on(next);
        }
        String status = text(show, "status");
        if (status != null && ENDED.contains(status)) {
            return Continuation.TERMINATA;
        }
        if ((status != null && CONTINUING.contains(status)) || show.path("in_production").asBoolean(false)) {
            return Continuation.PROSSIMAMENTE;
        }
        return Continuation.IGNOTA;
    }

    private JsonNode fetch(String kind, String id, List<String> append) {
        return CatalogCall.withRetry("TMDB " + kind + "/" + id, () -> http.get()
                .uri(b -> {
                    b.path("/" + kind + "/{id}")
                            .queryParam("api_key", cfg.apiKey())
                            .queryParam("language", "it-IT");
                    if (!append.isEmpty()) {
                        b.queryParam("append_to_response", String.join(",", append));
                    }
                    return b.build(id);
                })
                .retrieve()
                .body(JsonNode.class));
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

    private static Integer averageEpisodeRuntime(List<ContentDetail.Season> seasons) {
        List<Integer> runtimes = seasons.stream()
                .flatMap(s -> s.episodes().stream())
                .map(ContentDetail.Episode::runtimeMinutes)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (runtimes.isEmpty()) {
            return null;
        }
        return (int) Math.round(runtimes.stream().mapToInt(Integer::intValue).average().orElse(0));
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
