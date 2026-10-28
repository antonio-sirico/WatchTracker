package it.watchtracker.library;

import it.watchtracker.catalog.ContentDetail;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.catalog.Continuation;
import it.watchtracker.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Un contenuto salvato nella libreria personale di un utente (RF 2.5) con stato di visione,
 * avanzamento, rating e recensione. Conserva una copia dei dati del catalogo che servono alle
 * statistiche (generi, durata, numero di episodi) così che restino disponibili anche quando il
 * catalogo esterno non risponde (RNF-R5).
 */
@Entity
@Table(name = "library_entries", uniqueConstraints =
        @UniqueConstraint(name = "uk_library_user_content", columnNames = {"user_id", "source", "external_id"}))
public class LibraryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentSource source;

    @Column(name = "external_id", nullable = false, length = 32)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "release_year")
    private Integer year;

    @Column(name = "poster_url", length = 512)
    private String posterUrl;

    /** Generi separati da "|". */
    @Column(length = 512)
    private String genres;

    /** Durata del film oppure di un episodio, in minuti. */
    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "season_count")
    private Integer seasonCount;

    @Column(name = "episode_count")
    private Integer episodeCount;

    /** RF 3.7: stato di prosecuzione e data del prossimo episodio, se nota. */
    @Enumerated(EnumType.STRING)
    @Column(name = "continuation", length = 20)
    private Continuation.Status continuation;

    @Column(name = "next_episode_date")
    private LocalDate nextEpisodeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WatchStatus status = WatchStatus.DA_VEDERE;

    /** RF 3.8: rating da 1 a 5 stelle, unico per contenuto. */
    @Column
    private Integer rating;

    @Column(name = "rated_at")
    private Instant ratedAt;

    /** RF 3.9: recensione facoltativa (massimo 2000 caratteri). */
    @Column(length = 2000)
    private String review;

    @Column(name = "review_updated_at")
    private Instant reviewUpdatedAt;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EpisodeProgress> watched = new ArrayList<>();

    protected LibraryEntry() {}

    public LibraryEntry(User user, ContentSource source, String externalId, ContentType type, String title) {
        this.user = user;
        this.source = source;
        this.externalId = externalId;
        this.type = type;
        this.title = title;
    }

    public static LibraryEntry from(User user, ContentDetail d) {
        LibraryEntry e = new LibraryEntry(user, d.source(), d.externalId(), d.type(), d.title());
        e.applySnapshot(d);
        return e;
    }

    /** Aggiorna la copia dei dati di catalogo (titolo, locandina, generi, durata, episodi, prosecuzione). */
    public void applySnapshot(ContentDetail d) {
        int listed = d.listedEpisodes();
        applySnapshot(d.title(), d.type(), d.year(), d.posterUrl(), d.genres(), d.runtimeMinutes(), d.seasonCount(),
                d.episodeCount() != null ? d.episodeCount() : (listed > 0 ? listed : null),
                d.continuation() == null ? null : d.continuation().status(),
                d.continuation() == null ? null : d.continuation().nextDate());
    }

    public void applySnapshot(String title, ContentType type, Integer year, String posterUrl, List<String> genres,
                              Integer runtimeMinutes, Integer seasonCount, Integer episodeCount,
                              Continuation.Status continuation, LocalDate nextEpisodeDate) {
        if (title != null) {
            this.title = title;
        }
        if (type != null) {
            this.type = type;
        }
        this.year = year;
        this.posterUrl = posterUrl;
        this.genres = genres == null || genres.isEmpty() ? null : String.join("|", genres);
        this.runtimeMinutes = runtimeMinutes;
        this.seasonCount = seasonCount;
        this.episodeCount = episodeCount;
        if (continuation != null) {
            this.continuation = continuation;
            this.nextEpisodeDate = nextEpisodeDate;
        }
    }

    /** Importazione di un backup (UC-21): riporta rating e recensione con le date originali. */
    public void restoreRating(Integer rating, Instant ratedAt, String review, Instant reviewUpdatedAt) {
        this.rating = rating;
        this.ratedAt = rating == null ? null : ratedAt;
        this.review = review == null || review.isBlank() ? null : review;
        this.reviewUpdatedAt = this.review == null ? null : reviewUpdatedAt;
    }

    /** Importazione di un backup: riporta le date originali (solo prima del primo salvataggio per addedAt). */
    public void restoreTimestamps(Instant addedAt, Instant updatedAt, Instant completedAt) {
        if (addedAt != null) {
            this.addedAt = addedAt;
        }
        if (updatedAt != null) {
            this.updatedAt = updatedAt;
        }
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public ContentSource getSource() { return source; }
    public String getExternalId() { return externalId; }
    public ContentType getType() { return type; }
    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public String getPosterUrl() { return posterUrl; }
    public Integer getRuntimeMinutes() { return runtimeMinutes; }
    public Integer getSeasonCount() { return seasonCount; }
    public Integer getEpisodeCount() { return episodeCount; }
    public Continuation.Status getContinuation() { return continuation; }
    public LocalDate getNextEpisodeDate() { return nextEpisodeDate; }
    public WatchStatus getStatus() { return status; }
    public Integer getRating() { return rating; }
    public Instant getRatedAt() { return ratedAt; }
    public String getReview() { return review; }
    public Instant getReviewUpdatedAt() { return reviewUpdatedAt; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<EpisodeProgress> getWatched() { return watched; }

    public List<String> genreList() {
        return genres == null ? List.of() : Arrays.asList(genres.split("\\|"));
    }

    public boolean isSeries() {
        return type != ContentType.FILM;
    }

    public Optional<Continuation> continuation() {
        return continuation == null ? Optional.empty() : Optional.of(new Continuation(continuation, nextEpisodeDate));
    }

    /** RF 2.7, RF 3.1: cambio di stato; il primo passaggio a Completato registra la data. */
    public void setStatus(WatchStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
        if (status == WatchStatus.COMPLETATO && completedAt == null) {
            completedAt = Instant.now();
        }
    }

    public Optional<EpisodeProgress> progressOf(EpisodeRef ref) {
        return watched.stream().filter(p -> p.getSeason() == ref.season() && p.getEpisode() == ref.episode()).findFirst();
    }

    public boolean isWatched(EpisodeRef ref) {
        return progressOf(ref).isPresent();
    }

    /** RF 3.3: contrassegna un episodio come visto; restituisce true se non lo era già. */
    public boolean markWatched(EpisodeRef ref, Instant when) {
        if (isWatched(ref)) {
            return false;
        }
        watched.add(new EpisodeProgress(this, ref.season(), ref.episode(), when));
        updatedAt = Instant.now();
        return true;
    }

    /** RF 3.3: annulla il contrassegno di un episodio. */
    public boolean markUnwatched(EpisodeRef ref) {
        boolean removed = watched.removeIf(p -> p.getSeason() == ref.season() && p.getEpisode() == ref.episode());
        if (removed) {
            updatedAt = Instant.now();
        }
        return removed;
    }

    public int watchedCount() {
        return watched.size();
    }

    /** RF 3.6: tutti gli episodi disponibili risultano visti. */
    public boolean allEpisodesWatched() {
        return isSeries() && episodeCount != null && episodeCount > 0 && watched.size() >= episodeCount;
    }

    /** RF 3.8: rating unico per contenuto. */
    public void setRating(Integer rating, Instant when) {
        this.rating = rating;
        this.ratedAt = rating == null ? null : when;
        this.updatedAt = Instant.now();
    }

    /** RF 3.9 / RF 3.16: recensione facoltativa. */
    public void setReview(String review, Instant when) {
        this.review = review == null || review.isBlank() ? null : review.trim();
        this.reviewUpdatedAt = this.review == null ? null : when;
        this.updatedAt = Instant.now();
    }

    /** Usato dall'importazione del backup per riportare la data di completamento originale. */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void touch(Instant updatedAt) {
        if (updatedAt != null && updatedAt.isAfter(this.updatedAt)) {
            this.updatedAt = updatedAt;
        }
    }
}
