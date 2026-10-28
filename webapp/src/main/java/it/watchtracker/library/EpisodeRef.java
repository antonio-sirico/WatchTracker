package it.watchtracker.library;

import jakarta.validation.constraints.Min;

/** Riferimento a un episodio: numero di stagione e di episodio (per gli anime la stagione è 1). */
public record EpisodeRef(@Min(1) int season, @Min(1) int episode) implements Comparable<EpisodeRef> {

    @Override
    public int compareTo(EpisodeRef o) {
        return season != o.season ? Integer.compare(season, o.season) : Integer.compare(episode, o.episode);
    }

    @Override
    public String toString() {
        return "S" + season + " E" + episode;
    }
}
