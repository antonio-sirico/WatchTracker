package it.watchtracker.catalog;

import static it.watchtracker.catalog.JsonSupport.integer;
import static it.watchtracker.catalog.JsonSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import it.watchtracker.config.CatalogProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Client Jikan (MyAnimeList): anime (RF 2.1). */
@Component
public class JikanClient {

    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*hr");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*min");

    private final RestClient http;

    public JikanClient(RestClient.Builder builder, CatalogProperties props) {
        this.http = builder.baseUrl(props.jikan().baseUrl()).build();
    }

    public List<ContentSummary> search(String query) {
        JsonNode root = http.get()
                .uri(b -> b.path("/anime")
                        .queryParam("q", "{q}")
                        .queryParam("limit", "10")
                        .queryParam("sfw", "true")
                        .build(query))
                .retrieve()
                .body(JsonNode.class);

        List<ContentSummary> out = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            out.add(toSummary(n));
        }
        return out;
    }

    public ContentDetail detail(String id) {
        JsonNode n = http.get()
                .uri(b -> b.path("/anime/{id}").build(id))
                .retrieve()
                .body(JsonNode.class)
                .path("data");

        Integer episodes = integer(n, "episodes");
        List<ContentDetail.Season> seasons = episodes == null
                ? List.of()
                : List.of(new ContentDetail.Season(1, "Episodi", episodes));

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
                null,
                episodes,
                seasons,
                text(n, "status"));
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
