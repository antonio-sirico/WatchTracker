package it.watchtracker.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.watchtracker.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    private final CatalogService service = new CatalogService(null, null);

    private static ContentSummary tmdb(ContentType type, String title, String original) {
        return new ContentSummary(ContentSource.TMDB_TV, "1", type, title, original, 1999, null, null,
                List.of(), original == null ? List.of() : List.of(original));
    }

    private static ContentSummary mal(String english, String romaji, String... alternatives) {
        return new ContentSummary(ContentSource.MAL, "21", ContentType.ANIME, english, romaji, 1999, null, null,
                List.of(), List.of(alternatives));
    }

    @Test
    void rf24_animeOnBothCatalogsIsShownOnce() {
        List<ContentSummary> merged = service.merge(
                List.of(tmdb(ContentType.ANIME, "One Piece", "ONE PIECE"),
                        tmdb(ContentType.SERIE_TV, "One Piece", "One Piece")),
                List.of(mal("One Piece", "One Piece", "ワンピース")));

        assertThat(merged).hasSize(2);
        assertThat(merged).extracting(ContentSummary::source)
                .containsExactly(ContentSource.TMDB_TV, ContentSource.MAL);
        assertThat(merged.get(0).type()).isEqualTo(ContentType.SERIE_TV); // il live action resta
    }

    @Test
    void animeWithoutMalMatchIsKept() {
        List<ContentSummary> merged = service.merge(
                List.of(tmdb(ContentType.ANIME, "Akira", "AKIRA")),
                List.of(mal("Attack on Titan", "Shingeki no Kyojin")));

        assertThat(merged).hasSize(2);
    }

    @Test
    void titleKeyIgnoresCaseAccentsAndPunctuation() {
        assertThat(CatalogService.key("L'Attacco dei Giganti: Stagione 2!"))
                .isEqualTo(CatalogService.key("lattacco dei giganti stagione 2"));
    }

    @Test
    void jsonExposesSourceNameAndHidesAlternativeTitles() throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(mal("One Piece", "One Piece", "ワンピース"));

        assertThat(json).contains("\"sourceName\":\"MyAnimeList\"");
        assertThat(json).doesNotContain("alternativeTitles");
    }

    @Test
    void jikanDurationParsing() {
        assertThat(JikanClient.episodeMinutes("24 min per ep")).isEqualTo(24);
        assertThat(JikanClient.episodeMinutes("1 hr 55 min")).isEqualTo(115);
        assertThat(JikanClient.episodeMinutes("Unknown")).isNull();
        assertThat(JikanClient.episodeMinutes(null)).isNull();
    }

    @Test
    void uc06_searchTextMustHaveAtLeastTwoCharacters() {
        assertThatThrownBy(() -> service.search("a"))
                .isInstanceOf(ApiException.class)
                .hasMessage(CatalogService.QUERY_ERROR)
                .extracting(e -> ((ApiException) e).getFields().get("q"))
                .isEqualTo(CatalogService.QUERY_ERROR);
        assertThatThrownBy(() -> service.search("x".repeat(101))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search(null)).isInstanceOf(ApiException.class);
    }
}
