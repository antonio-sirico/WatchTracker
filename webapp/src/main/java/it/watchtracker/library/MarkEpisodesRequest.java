package it.watchtracker.library;

import it.watchtracker.catalog.ContentSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * UC-13, UC-14, UC-15: contrassegna uno o più episodi come visti (o non visti) in un'unica
 * operazione. Se il contenuto non è ancora in libreria viene inserito automaticamente (RF 3.17).
 */
public record MarkEpisodesRequest(
        @NotNull(message = "Sorgente obbligatoria") ContentSource source,
        @NotBlank(message = "Identificativo obbligatorio") @Size(max = 32) String externalId,
        @NotEmpty(message = "Indica almeno un episodio") @Valid List<EpisodeRef> episodes,
        boolean watched) {}
