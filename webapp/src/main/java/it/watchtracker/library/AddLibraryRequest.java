package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import it.watchtracker.catalog.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddLibraryRequest(
        @NotNull(message = "Sorgente obbligatoria") ContentSource source,
        @NotBlank(message = "Identificativo obbligatorio") @Size(max = 32) String externalId,
        @NotNull(message = "Tipologia obbligatoria") ContentType type,
        @NotBlank(message = "Titolo obbligatorio") @Size(max = 255) String title,
        Integer year,
        @Size(max = 512) String posterUrl) {}
