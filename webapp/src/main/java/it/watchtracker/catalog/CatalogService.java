package it.watchtracker.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Ricerca unificata su TMDB e MyAnimeList con deduplicazione degli anime (RF 0.2, 0.3, 2.1-2.4). */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final TmdbClient tmdb;
    private final JikanClient jikan;

    public CatalogService(TmdbClient tmdb, JikanClient jikan) {
        this.tmdb = tmdb;
        this.jikan = jikan;
    }

    public SearchResponse search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new SearchResponse(q, List.of(), List.of());
        }

        List<String> warnings = new ArrayList<>();
        List<ContentSummary> fromTmdb = List.of();
        List<ContentSummary> fromMal = List.of();

        if (!tmdb.isConfigured()) {
            warnings.add("Chiave API TMDB non configurata: film e serie TV non disponibili.");
        } else {
            try {
                fromTmdb = tmdb.search(q);
            } catch (RuntimeException e) {
                log.warn("TMDB non raggiungibile: {}", e.getMessage());
                warnings.add("TMDB non raggiungibile: film e serie TV non disponibili.");
            }
        }
        try {
            fromMal = jikan.search(q);
        } catch (RuntimeException e) {
            log.warn("Jikan non raggiungibile: {}", e.getMessage());
            warnings.add("MyAnimeList non raggiungibile: anime non disponibili.");
        }

        return new SearchResponse(q, merge(fromTmdb, fromMal), warnings);
    }

    public ContentDetail detail(ContentSource source, String id) {
        return source == ContentSource.MAL ? jikan.detail(id) : tmdb.detail(source, id);
    }

    /**
     * RF 2.4: un anime presente sia su TMDB sia su MyAnimeList viene mostrato una sola volta,
     * privilegiando la scheda di MyAnimeList. Il confronto avviene sui titoli normalizzati.
     */
    List<ContentSummary> merge(List<ContentSummary> fromTmdb, List<ContentSummary> fromMal) {
        Set<String> malTitles = new HashSet<>();
        for (ContentSummary anime : fromMal) {
            addKey(malTitles, anime.title());
            addKey(malTitles, anime.originalTitle());
            anime.alternativeTitles().forEach(t -> addKey(malTitles, t));
        }

        List<ContentSummary> merged = new ArrayList<>();
        for (ContentSummary c : fromTmdb) {
            boolean duplicate = c.type() == ContentType.ANIME
                    && (malTitles.contains(key(c.title())) || malTitles.contains(key(c.originalTitle())));
            if (!duplicate) {
                merged.add(c);
            }
        }
        merged.addAll(fromMal);
        return merged;
    }

    private static void addKey(Set<String> keys, String title) {
        String k = key(title);
        if (!k.isEmpty()) {
            keys.add(k);
        }
    }

    /** Minuscolo, senza accenti e senza caratteri non alfanumerici. */
    static String key(String title) {
        if (title == null) {
            return "";
        }
        return Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
