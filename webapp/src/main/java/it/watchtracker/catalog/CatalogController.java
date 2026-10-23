package it.watchtracker.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    /** RF 0.2 / RF 0.3: ricerca per testo, accessibile anche al visitatore. */
    @GetMapping("/search")
    public SearchResponse search(@RequestParam("q") String query) {
        return service.search(query);
    }

    /** RF 0.4: scheda del contenuto. */
    @GetMapping("/{source}/{id}")
    public ContentDetail detail(@PathVariable ContentSource source, @PathVariable String id) {
        return service.detail(source, id);
    }
}
