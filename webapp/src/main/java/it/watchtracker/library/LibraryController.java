package it.watchtracker.library;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<LibraryEntryDto> list(HttpSession session) {
        return library.list(currentUser.require(session));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryEntryDto add(@Valid @RequestBody AddLibraryRequest request, HttpSession session) {
        return library.add(currentUser.require(session), request);
    }

    @PatchMapping("/{id}/status")
    public LibraryEntryDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                        HttpSession session) {
        User user = currentUser.require(session);
        return library.updateStatus(user, id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, HttpSession session) {
        library.remove(currentUser.require(session), id);
    }
}
