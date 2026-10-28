package it.watchtracker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Utente registrato. */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    /** RF 1.4: profilo pubblico o privato (privato per default, UC-01 passo 4). */
    @Column(name = "public_profile", nullable = false)
    private boolean publicProfile = false;

    /** RF 1.4: sezioni del profilo pubblico che il proprietario rende visibili. */
    @Column(name = "show_library", nullable = false)
    private boolean showLibrary = true;

    @Column(name = "show_stats", nullable = false)
    private boolean showStats = true;

    @Column(name = "show_reviews", nullable = false)
    private boolean showReviews = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String nickname, String email, String passwordHash) {
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isPublicProfile() { return publicProfile; }
    public boolean isShowLibrary() { return showLibrary; }
    public boolean isShowStats() { return showStats; }
    public boolean isShowReviews() { return showReviews; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public void setShowLibrary(boolean showLibrary) { this.showLibrary = showLibrary; }
    public void setShowStats(boolean showStats) { this.showStats = showStats; }
    public void setShowReviews(boolean showReviews) { this.showReviews = showReviews; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
