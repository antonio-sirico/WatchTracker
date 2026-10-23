package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.user.User;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Un contenuto salvato nella libreria personale di un utente (RF 2.5). */
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WatchStatus status = WatchStatus.DA_VEDERE;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LibraryEntry() {}

    public LibraryEntry(User user, ContentSource source, String externalId, ContentType type,
                        String title, Integer year, String posterUrl) {
        this.user = user;
        this.source = source;
        this.externalId = externalId;
        this.type = type;
        this.title = title;
        this.year = year;
        this.posterUrl = posterUrl;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public ContentSource getSource() { return source; }
    public String getExternalId() { return externalId; }
    public ContentType getType() { return type; }
    public String getTitle() { return title; }
    public Integer getYear() { return year; }
    public String getPosterUrl() { return posterUrl; }
    public WatchStatus getStatus() { return status; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(WatchStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
