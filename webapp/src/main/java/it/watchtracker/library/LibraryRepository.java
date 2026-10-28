package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryRepository extends JpaRepository<LibraryEntry, Long> {

    List<LibraryEntry> findByUserOrderByAddedAtDesc(User user);

    Optional<LibraryEntry> findByIdAndUser(Long id, User user);

    Optional<LibraryEntry> findByUserAndSourceAndExternalId(User user, ContentSource source, String externalId);

    boolean existsByUserAndSourceAndExternalId(User user, ContentSource source, String externalId);

    /** Contenuti con l'elenco completo degli episodi visti (statistiche e backup). */
    @Query("select distinct e from LibraryEntry e left join fetch e.watched where e.user = :user order by e.addedAt desc")
    List<LibraryEntry> findAllWithProgress(@Param("user") User user);

    /** Numero di episodi visti per ciascun contenuto dell'utente. */
    @Query("select p.entry.id, count(p) from EpisodeProgress p where p.entry.user = :user group by p.entry.id")
    List<Object[]> countWatchedByEntry(@Param("user") User user);
}
