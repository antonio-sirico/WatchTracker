package it.watchtracker.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Un episodio contrassegnato come visto (RF 3.3, RF 3.5): l'avanzamento di un contenuto. */
@Entity
@Table(name = "episode_progress", uniqueConstraints =
        @UniqueConstraint(name = "uk_progress_entry_episode", columnNames = {"entry_id", "season", "episode"}))
public class EpisodeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private LibraryEntry entry;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private int episode;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt = Instant.now();

    protected EpisodeProgress() {}

    public EpisodeProgress(LibraryEntry entry, int season, int episode, Instant watchedAt) {
        this.entry = entry;
        this.season = season;
        this.episode = episode;
        this.watchedAt = watchedAt == null ? Instant.now() : watchedAt;
    }

    public Long getId() { return id; }
    public LibraryEntry getEntry() { return entry; }
    public int getSeason() { return season; }
    public int getEpisode() { return episode; }
    public Instant getWatchedAt() { return watchedAt; }

    public EpisodeRef ref() {
        return new EpisodeRef(season, episode);
    }
}
