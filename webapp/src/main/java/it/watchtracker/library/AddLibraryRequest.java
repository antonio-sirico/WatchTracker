package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-10: inserimento in libreria. Il client indica solo il contenuto del catalogo; i dati
 * (titolo, locandina, generi, durata, episodi) vengono letti dal catalogo di origine.
 */
public record AddLibraryRequest(
        @NotNull(message = "Sorgente obbligatoria") ContentSource source,
        @NotBlank(message = "Identificativo obbligatorio") @Size(max = 32) String externalId) {}
