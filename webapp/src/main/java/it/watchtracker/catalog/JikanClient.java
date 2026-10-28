package it.watchtracker.catalog;

import static it.watchtracker.catalog.JsonSupport.integer;
import static it.watchtracker.catalog.JsonSupport.localDate;
import static it.watchtracker.catalog.JsonSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import it.watchtracker.config.CatalogProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Client Jikan (MyAnimeList): anime (RF 2.1). */
@Component
public class JikanClient {

    private static final Logger log = LoggerFactory.getLogger(JikanClient.class);
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*hr");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*min");
    /** Jikan restituisce al massimo 100 episodi per pagina. */
    private static final int EPISODES_PER_PAGE = 100;

    private final RestClient http;
    private final Clock clock;

    @Autowired
    public JikanClient(RestClient.Builder builder, CatalogProperties props) {
        this(builder, props, Clock.systemUTC());
    }

    JikanClient(RestClient.Builder builder, CatalogProperties props, Clock clock) {
        this.http = builder.baseUrl(props.jikan().baseUrl()).build();
        this.clock = clock;
    }

    public List<ContentSummary> search(String query) {
        JsonNode root = CatalogCall.withRetry("Jikan search", () -> http.get()
                .uri(b -> b.path("/anime")
                        .queryParam("q", "{q}")
                        .queryParam("limit", "10")
                        .queryParam("sfw", "true")
                        .build(query))
                .retrieve()
                .body(JsonNode.class));

        List<ContentSummary> out = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            out.add(toSummary(n));
        }
        return out;
    }

    public ContentDetail detail(String id) {
        JsonNode n = CatalogCall.withRetry("Jikan anime/" + id, () -> http.get()
                .uri(b -> b.path("/anime/{id}").build(id))
                .retrieve()
                .body(JsonNode.class))
                .path("data");

        Integer declared = integer(n, "episodes");
        List<ContentDetail.Episode> episodes = episodes(id, declared);
        Integer total = declared != null ? declared : (episodes.isEmpty() ? null : episodes.size());
        List<ContentDetail.Season> seasons = episodes.isEmpty()
                ? List.of()
                : List.of(new ContentDetail.Season(1, "Episodi", episodes.size(), episodes));

        return new ContentDetail(
                ContentSource.MAL,
                id,
                ContentType.ANIME,
                displayTitle(n),
                text(n, "title"),
                year(n),
                poster(n),
                text(n, "synopsis"),
                genres(n),
                episodeMinutes(text(n, "duration")),
                seasons.isEmpty() ? null : 1,
                total,
                seasons,
                text(n, "status"),
                continuation(n, LocalDate.now(clock)));
    }

    /**
     * RF 3.2: elenco degli episodi. MyAnimeList non raggruppa in stagioni: tutti gli episodi
     * stanno in un'unica "stagione 1". I titoli arrivano dalla prima pagina dell'endpoint
     * episodi; per gli anime in corso, senza numero dichiarato, il totale si ricava
     * dall'ultima pagina. Se l'endpoint non risponde si usa il solo numero dichiarato.
     */
    private List<ContentDetail.Episode> episodes(String id, Integer declared) {
        Map<Integer, String> names = new HashMap<>();
        Integer counted = null;
        try {
            JsonNode first = episodesPage(id, 1);
            for (JsonNode e : first.path("data")) {
                names.put(e.path("mal_id").asInt(), text(e, "title"));
            }
            int lastPage = Math.max(1, first.path("pagination").path("last_visible_page").asInt(1));
            if (lastPage == 1) {
                counted = first.path("data").size();
            } else if (declared == null) {
                JsonNode last = episodesPage(id, lastPage);
                counted = (lastPage - 1) * EPISODES_PER_PAGE + last.path("data").size();
            }
        } catch (RuntimeException e) {
            log.warn("Jikan: elenco episodi di {} non disponibile ({})", id, e.getMessage());
        }
        int total = declared != null ? declared : (counted != null ? counted : 0);
        List<ContentDetail.Episode> out = new ArrayList<>(total);
        for (int i = 1; i <= total; i++) {
            out.add(new ContentDetail.Episode(i, names.get(i), null, null));
        }
        return out;
    }

    private JsonNode episodesPage(String id, int page) {
        return CatalogCall.withRetry("Jikan anime/" + id + "/episodes", () -> http.get()
                .uri(b -> b.path("/anime/{id}/episodes").queryParam("page", page).build(id))
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * RF 3.7: stato di prosecuzione. Per un anime in corso MyAnimeList non indica la data del
     * prossimo episodio ma il giorno della settimana di trasmissione, da cui si calcola la
     * prossima data prevista.
     */
    static Continuation continuation(JsonNode anime, LocalDate today) {
        String status = text(anime, "status");
        if (status == null) {
            return Continuation.IGNOTA;
        }
        switch (status) {
            case "Finished Airing":
                return Continuation.TERMINATA;
            case "Currently Airing": {
                DayOfWeek day = broadcastDay(text(anime.path("broadcast"), "day"));
                return day == null ? Continuation.PROSSIMAMENTE
                        : Continuation.on(today.with(TemporalAdjusters.nextOrSame(day)));
            }
            case "Not yet aired": {
                LocalDate from = localDate(text(anime.path("aired"), "from"));
                return from != null && !from.isBefore(today) ? Continuation.on(from) : Continuation.PROSSIMAMENTE;
            }
            default:
                return Continuation.IGNOTA;
        }
    }

    /** Jikan riporta il giorno come "Sundays", "Mondays", ... */
    static DayOfWeek broadcastDay(String day) {
        if (day == null) {
            return null;
        }
        String d = day.trim().toUpperCase(Locale.ROOT);
        if (d.endsWith("S")) {
            d = d.substring(0, d.length() - 1);
        }
        try {
            return DayOfWeek.valueOf(d);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ContentSummary toSummary(JsonNode n) {
        List<String> alternatives = new ArrayList<>();
        for (JsonNode t : n.path("titles")) {
            String title = text(t, "title");
            if (title != null) {
                alternatives.add(title);
            }
        }
        return new ContentSummary(
                ContentSource.MAL,
                n.path("mal_id").asText(),
                ContentType.ANIME,
                displayTitle(n),
                text(n, "title"),
                year(n),
                poster(n),
                text(n, "synopsis"),
                genres(n),
                alternatives);
    }

    /** Titolo inglese se disponibile, altrimenti quello romaji. */
    private static String displayTitle(JsonNode n) {
        String english = text(n, "title_english");
        return english != null ? english : text(n, "title");
    }

    private static Integer year(JsonNode n) {
        Integer year = integer(n, "year");
        if (year != null) {
            return year;
        }
        return integer(n.path("aired").path("prop").path("from"), "year");
    }

    private static String poster(JsonNode n) {
        JsonNode jpg = n.path("images").path("jpg");
        String url = text(jpg, "large_image_url");
        return url != null ? url : text(jpg, "image_url");
    }

    private static List<String> genres(JsonNode n) {
        List<String> genres = new ArrayList<>();
        for (String field : List.of("genres", "themes", "demographics")) {
            for (JsonNode g : n.path(field)) {
                String name = text(g, "name");
                if (name != null) {
                    genres.add(name);
                }
            }
        }
        return genres;
    }

    /** Jikan riporta la durata come testo, es. "24 min per ep" o "1 hr 55 min". */
    static Integer episodeMinutes(String duration) {
        if (duration == null) {
            return null;
        }
        int total = 0;
        Matcher hours = HOURS.matcher(duration);
        if (hours.find()) {
            total += Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher minutes = MINUTES.matcher(duration);
        if (minutes.find()) {
            total += Integer.parseInt(minutes.group(1));
        }
        return total > 0 ? total : null;
    }
}
