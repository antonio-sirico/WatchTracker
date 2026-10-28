package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.common.ApiException;
import it.watchtracker.user.CurrentUser;
import it.watchtracker.user.User;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService library;
    private final CurrentUser currentUser;

    public LibraryController(LibraryService library, CurrentUser currentUser) {
        this.library = library;
        this.currentUser = currentUser;
    }

    /** UC-11: elenco della libreria (filtri e ordinamento sono applicati dal client). */
    @GetMapping
    public List<LibraryEntryDto> list(HttpSession session) {
        return library.list(currentUser.require(session));
    }

    /** Voce di libreria di un contenuto del catalogo, con gli episodi visti (404 se assente). */
    @GetMapping("/entry")
    public LibraryEntryDto entry(@RequestParam ContentSource source, @RequestParam String externalId,
                                 HttpSession session) {
        return library.find(currentUser.require(session), source, externalId)
                .orElseThrow(() -> ApiException.notFound(LibraryService.NOT_IN_LIBRARY));
    }

    /** UC-10: inserimento in libreria. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryEntryDto add(@Valid @RequestBody AddLibraryRequest request, HttpSession session) {
        return library.add(currentUser.require(session), request.source(), request.externalId());
    }

    /** UC-12: cambio di stato. */
    @PatchMapping("/{id}/status")
    public LibraryEntryDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                        HttpSession session) {
        User user = currentUser.require(session);
        return library.updateStatus(user, id, request.status());
    }

    /** UC-12: rimozione dalla libreria. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, HttpSession session) {
        library.remove(currentUser.require(session), id);
    }

    /** UC-13, UC-14, UC-15: registrazione di episodi visti o non visti. */
    @PostMapping("/episodes")
    public LibraryService.MarkResult markEpisodes(@Valid @RequestBody MarkEpisodesRequest request,
                                                  HttpSession session) {
        return library.markEpisodes(currentUser.require(session), request);
    }

    /** UC-17: rating e recensione. */
    @PutMapping("/{id}/rating")
    public LibraryEntryDto rate(@PathVariable Long id, @Valid @RequestBody RatingRequest request,
                                HttpSession session) {
        return library.rate(currentUser.require(session), id, request);
    }

    /** UC-17 E4: rimozione del rating (e, se richiesto, della recensione). */
    @DeleteMapping("/{id}/rating")
    public LibraryEntryDto removeRating(@PathVariable Long id,
                                        @RequestParam(name = "review", defaultValue = "false") boolean alsoReview,
                                        HttpSession session) {
        return library.removeRating(currentUser.require(session), id, alsoReview);
    }

    public record ReviewRequest(String review) {}

    /** UC-18: modifica della recensione. */
    @PutMapping("/{id}/review")
    public LibraryEntryDto updateReview(@PathVariable Long id, @RequestBody ReviewRequest request,
                                        HttpSession session) {
        return library.updateReview(currentUser.require(session), id, request.review());
    }

    /** UC-18: cancellazione della recensione. */
    @DeleteMapping("/{id}/review")
    public LibraryEntryDto removeReview(@PathVariable Long id, HttpSession session) {
        return library.updateReview(currentUser.require(session), id, null);
    }
}
