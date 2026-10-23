package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryRepository extends JpaRepository<LibraryEntry, Long> {

    List<LibraryEntry> findByUserOrderByAddedAtDesc(User user);

    Optional<LibraryEntry> findByIdAndUser(Long id, User user);

    boolean existsByUserAndSourceAndExternalId(User user, ContentSource source, String externalId);
}
