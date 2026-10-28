package it.watchtracker.library;

import it.watchtracker.catalog.CatalogService;
import it.watchtracker.catalog.ContentDetail;
import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import it.watchtracker.common.ApiException;
import it.watchtracker.user.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {

    /** UC-10 E1. */
    public static final String ALREADY_PRESENT = "Questo contenuto è già nella tua libreria";
    public static final String NOT_IN_LIBRARY = "Contenuto non presente in libreria";

    /** Esito di UC-13/UC-15: contenuto aggiornato, se è stato inserito ora (RF 3.17) e se è tutto visto (RF 3.6). */
    public record MarkResult(LibraryEntryDto entry, boolean added, boolean allWatched, int changed) {}

    private final LibraryRepository repository;
    private final CatalogService catalog;

    public LibraryService(LibraryRepository repository, CatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    /** RF 2.8: elenco dei contenuti in libreria con stato e avanzamento. */
    @Transactional(readOnly = true)
    public List<LibraryEntryDto> list(User user) {
        Map<Long, Integer> counts = watchedCounts(user);
        return repository.findByUserOrderByAddedAtDesc(user).stream()
                .map(e -> LibraryEntryDto.summary(e, counts.getOrDefault(e.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> watchedCounts(User user) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : repository.countWatchedByEntry(user)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /** La voce di libreria di un contenuto del catalogo, con gli episodi visti. */
    @Transactional(readOnly = true)
    public Optional<LibraryEntryDto> find(User user, ContentSource source, String externalId) {
        return repository.findByUserAndSourceAndExternalId(user, source, externalId).map(LibraryEntryDto::full);
    }

    /** RF 2.5 e RF 2.6 / UC-10: inserimento con stato iniziale "Da vedere" e dati letti dal catalogo. */
    @Transactional
    public LibraryEntryDto add(User user, ContentSource source, String externalId) {
        if (repository.existsByUserAndSourceAndExternalId(user, source, externalId)) {
            throw ApiException.conflict(ALREADY_PRESENT);
        }
        ContentDetail detail = catalog.detail(source, externalId);
        return LibraryEntryDto.full(repository.save(LibraryEntry.from(user, detail)));
    }

    /** RF 2.7 e RF 3.1 / UC-12: cambio dello stato di visione. */
    @Transactional
    public LibraryEntryDto updateStatus(User user, Long entryId, WatchStatus status) {
        LibraryEntry entry = find(user, entryId);
        entry.setStatus(status);
        return LibraryEntryDto.full(repository.save(entry));
    }

    /** RF 2.11 / UC-12: rimozione, insieme ad avanzamento, rating e recensione. */
    @Transactional
    public void remove(User user, Long entryId) {
        repository.delete(find(user, entryId));
    }

    /**
     * RF 3.3, RF 3.4, RF 3.5, RF 3.17 / UC-13, UC-14, UC-15: registra la visione (o la annulla) di
     * uno o più episodi. Un contenuto Da vedere o Abbandonato passa In corso; se al termine tutti
     * gli episodi disponibili risultano visti, l'esito lo segnala per la proposta di completamento.
     */
    @Transactional
    public MarkResult markEpisodes(User user, MarkEpisodesRequest request) {
        boolean added = false;
        Optional<LibraryEntry> existing =
                repository.findByUserAndSourceAndExternalId(user, request.source(), request.externalId());
        LibraryEntry entry;
        if (existing.isPresent()) {
            entry = existing.get();
        } else {
            entry = LibraryEntry.from(user, catalog.detail(request.source(), request.externalId()));
            added = true;
        }
        if (entry.getType() == ContentType.FILM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Un film non ha episodi da contrassegnare");
        }

        Instant now = Instant.now();
        int changed = 0;
        for (EpisodeRef ref : request.episodes()) {
            if (request.watched() ? entry.markWatched(ref, now) : entry.markUnwatched(ref)) {
                changed++;
            }
        }
        if (request.watched() && entry.getStatus() != WatchStatus.IN_CORSO
                && entry.getStatus() != WatchStatus.COMPLETATO) {
            entry.setStatus(WatchStatus.IN_CORSO);
        }
        entry = repository.save(entry);
        boolean allWatched = request.watched() && entry.allEpisodesWatched()
                && entry.getStatus() != WatchStatus.COMPLETATO;
        return new MarkResult(LibraryEntryDto.full(entry), added, allWatched, changed);
    }

    /** RF 3.8 e RF 3.9 / UC-17: rating e recensione, solo per i contenuti completati. */
    @Transactional
    public LibraryEntryDto rate(User user, Long entryId, RatingRequest request) {
        LibraryEntry entry = find(user, entryId);
        if (entry.getStatus() != WatchStatus.COMPLETATO) {
            throw new ApiException(HttpStatus.CONFLICT, RatingRequest.NOT_COMPLETED);
        }
        Instant now = Instant.now();
        entry.setRating(request.rating(), now);
        if (request.review() != null || entry.getReview() == null) {
            entry.setReview(request.review(), now);
        }
        return LibraryEntryDto.full(repository.save(entry));
    }

    /** UC-17 E4: rimozione del rating, eventualmente insieme alla recensione. */
    @Transactional
    public LibraryEntryDto removeRating(User user, Long entryId, boolean alsoReview) {
        LibraryEntry entry = find(user, entryId);
        entry.setRating(null, null);
        if (alsoReview) {
            entry.setReview(null, null);
        }
        return LibraryEntryDto.full(repository.save(entry));
    }

    /** RF 3.16 / UC-18: modifica o cancellazione della recensione. */
    @Transactional
    public LibraryEntryDto updateReview(User user, Long entryId, String review) {
        LibraryEntry entry = find(user, entryId);
        if (review != null && !review.isBlank() && (review.trim().length() < 10 || review.trim().length() > 2000)) {
            throw ApiException.badRequest("Dati non validi", "review", RatingRequest.REVIEW_MESSAGE);
        }
        if (review != null && !review.isBlank() && entry.getRating() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Assegna prima un rating al contenuto");
        }
        entry.setReview(review, Instant.now());
        return LibraryEntryDto.full(repository.save(entry));
    }

    /** Quando la scheda viene riaperta, la copia locale dei dati di catalogo viene aggiornata. */
    @Transactional
    public Optional<LibraryEntryDto> refreshSnapshot(User user, ContentDetail detail) {
        return repository.findByUserAndSourceAndExternalId(user, detail.source(), detail.externalId())
                .map(entry -> {
                    entry.applySnapshot(detail);
                    return LibraryEntryDto.full(repository.save(entry));
                });
    }

    private LibraryEntry find(User user, Long entryId) {
        return repository.findByIdAndUser(entryId, user)
                .orElseThrow(() -> ApiException.notFound(NOT_IN_LIBRARY));
    }
}
