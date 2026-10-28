package it.watchtracker.catalog;

import it.watchtracker.library.LibraryService;
import it.watchtracker.user.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService service;
    private final CurrentUser currentUser;
    private final LibraryService library;

    public CatalogController(CatalogService service, CurrentUser currentUser, LibraryService library) {
        this.service = service;
        this.currentUser = currentUser;
        this.library = library;
    }

    /** RF 0.2 / RF 0.3 / UC-06: ricerca per testo, accessibile anche al visitatore. */
    @GetMapping("/search")
    public SearchResponse search(@RequestParam("q") String query) {
        return service.search(query);
    }

    /**
     * RF 0.4, RF 3.2, RF 3.7 / UC-07: scheda del contenuto con stagioni, episodi e stato di
     * prosecuzione. Se l'utente è autenticato e il contenuto è in libreria, la copia locale dei
     * dati di catalogo viene aggiornata.
     */
    @GetMapping("/{source}/{id}")
    public ContentDetail detail(@PathVariable ContentSource source, @PathVariable String id, HttpSession session) {
        ContentDetail detail = service.detail(source, id);
        currentUser.find(session).ifPresent(user -> library.refreshSnapshot(user, detail));
        return detail;
    }
}
