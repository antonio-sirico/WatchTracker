package it.watchtracker.catalog;

/** Catalogo esterno di provenienza del contenuto (RF 2.1). */
public enum ContentSource {
    TMDB_MOVIE("TMDB"),
    TMDB_TV("TMDB"),
    MAL("MyAnimeList");

    private final String label;

    ContentSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
