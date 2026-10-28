package it.watchtracker;

import it.watchtracker.catalog.CatalogService;
import it.watchtracker.catalog.ContentDetail;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import it.watchtracker.catalog.SearchResponse;
import it.watchtracker.common.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Catalogo finto per i test di integrazione HTTP: nessuna chiamata a TMDB o Jikan. */
@TestConfiguration
public class FakeCatalog extends CatalogService {

    public FakeCatalog() {
        super(null, null);
    }

    @Bean
    @Primary
    public CatalogService fakeCatalogService() {
        return this;
    }

    @Override
    public SearchResponse search(String query) {
        return new SearchResponse(query, List.of(), List.of());
    }

    @Override
    public ContentDetail detail(ContentSource source, String id) {
        return switch (source + ":" + id) {
            case "MAL:21" -> anime("21", "One Piece", 4, Continuation.on(LocalDate.of(2026, 11, 1)));
            case "MAL:5114" -> anime("5114", "Fullmetal Alchemist: Brotherhood", 2, Continuation.TERMINATA);
            case "TMDB_MOVIE:550" -> new ContentDetail(ContentSource.TMDB_MOVIE, "550", ContentType.FILM,
                    "Fight Club", "Fight Club", 1999, null, "...", List.of("Dramma"), 139, null, null,
                    List.of(), "Released", null);
            case "TMDB_TV:66732" -> new ContentDetail(ContentSource.TMDB_TV, "66732", ContentType.SERIE_TV,
                    "Stranger Things", "Stranger Things", 2016, null, "...", List.of("Dramma", "Fantascienza"), 50,
                    2, 5, List.of(season(1, 3), season(2, 2)), "Returning Series", Continuation.PROSSIMAMENTE);
            default -> throw ApiException.notFound("Contenuto non trovato nel catalogo esterno");
        };
    }

    private static ContentDetail anime(String id, String title, int episodes, Continuation continuation) {
        return new ContentDetail(ContentSource.MAL, id, ContentType.ANIME, title, title, 1999, null, "...",
                List.of("Action", "Adventure"), 24, 1, episodes, List.of(season(1, episodes)),
                "Currently Airing", continuation);
    }

    private static ContentDetail.Season season(int number, int episodes) {
        List<ContentDetail.Episode> list = new ArrayList<>();
        for (int i = 1; i <= episodes; i++) {
            list.add(new ContentDetail.Episode(i, "Episodio " + i, null, null));
        }
        return new ContentDetail.Season(number, "Stagione " + number, episodes, list);
    }
}
