package it.watchtracker.library;

import it.watchtracker.common.ApiException;
import it.watchtracker.user.User;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {

    private final LibraryRepository repository;

    public LibraryService(LibraryRepository repository) {
        this.repository = repository;
    }

    /** RF 2.8: elenco dei contenuti in libreria. */
    @Transactional(readOnly = true)
    public List<LibraryEntryDto> list(User user) {
        return repository.findByUserOrderByAddedAtDesc(user).stream().map(LibraryEntryDto::from).toList();
    }

    /** RF 2.5 e RF 2.6: inserimento con stato iniziale "Da vedere". */
    @Transactional
    public LibraryEntryDto add(User user, AddLibraryRequest request) {
        if (repository.existsByUserAndSourceAndExternalId(user, request.source(), request.externalId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Contenuto già presente in libreria");
        }
        LibraryEntry entry = new LibraryEntry(user, request.source(), request.externalId(), request.type(),
                request.title(), request.year(), request.posterUrl());
        return LibraryEntryDto.from(repository.save(entry));
    }

    /** RF 2.7: cambio dello stato di visione. */
    @Transactional
    public LibraryEntryDto updateStatus(User user, Long entryId, WatchStatus status) {
        LibraryEntry entry = find(user, entryId);
        entry.setStatus(status);
        return LibraryEntryDto.from(repository.save(entry));
    }

    /** RF 2.11: rimozione dalla libreria. */
    @Transactional
    public void remove(User user, Long entryId) {
        repository.delete(find(user, entryId));
    }

    private LibraryEntry find(User user, Long entryId) {
        return repository.findByIdAndUser(entryId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contenuto non presente in libreria"));
    }
}
