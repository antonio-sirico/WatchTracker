package it.watchtracker.profile;

import it.watchtracker.common.ApiException;
import it.watchtracker.library.LibraryEntry;
import it.watchtracker.library.LibraryEntryDto;
import it.watchtracker.library.LibraryRepository;
import it.watchtracker.stats.StatsService;
import it.watchtracker.user.User;
import it.watchtracker.user.UserService;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profilo pubblico consultabile senza autenticazione (RF 0.5, RF 0.6, RF 4.1–4.4, RNF-R8, RNF-R10).
 * Nessuna operazione di scrittura è esposta: la consultazione è in sola lettura.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final UserService users;
    private final LibraryRepository library;
    private final StatsService stats;

    public ProfileController(UserService users, LibraryRepository library, StatsService stats) {
        this.users = users;
        this.library = library;
        this.stats = stats;
    }

    /** UC-08: profilo privato o inesistente restituisce lo stesso errore, senza rivelare nulla. */
    @GetMapping("/{nickname}")
    @Transactional(readOnly = true)
    public PublicProfileDto view(@PathVariable String nickname) {
        User owner = users.findByNickname(nickname)
                .filter(User::isPublicProfile)
                .orElseThrow(() -> ApiException.notFound(PublicProfileDto.NOT_AVAILABLE));

        List<LibraryEntry> entries = library.findAllWithProgress(owner);

        List<LibraryEntryDto> libraryView = owner.isShowLibrary()
                ? entries.stream().map(e -> LibraryEntryDto.summary(e, e.watchedCount())).toList()
                : null;
        List<PublicProfileDto.ReviewDto> reviews = owner.isShowReviews()
                ? entries.stream()
                        .filter(e -> e.getReview() != null)
                        .sorted(Comparator.comparing(LibraryEntry::getReviewUpdatedAt).reversed())
                        .map(e -> new PublicProfileDto.ReviewDto(e.getId(), e.getSource(), e.getExternalId(),
                                e.getType(), e.getTitle(), e.getYear(), e.getPosterUrl(), e.getRating(),
                                e.getReview(), e.getReviewUpdatedAt()))
                        .toList()
                : null;

        return new PublicProfileDto(owner.getNickname(), owner.getCreatedAt(),
                owner.isShowLibrary(), owner.isShowStats(), owner.isShowReviews(),
                libraryView,
                owner.isShowStats() ? stats.compute(entries) : null,
                reviews);
    }
}
