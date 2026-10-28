package it.watchtracker.library;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tabella dei dati di UC-17: rating da 1 a 5 e recensione facoltativa di 10–2000 caratteri. */
public record RatingRequest(
        @NotNull(message = RATING_MESSAGE)
        @Min(value = 1, message = RATING_MESSAGE)
        @Max(value = 5, message = RATING_MESSAGE)
        Integer rating,

        @Size(min = 10, max = 2000, message = REVIEW_MESSAGE)
        String review) {

    public static final String RATING_MESSAGE = "Seleziona un valore da 1 a 5 stelle";
    public static final String REVIEW_MESSAGE = "La recensione deve avere da 10 a 2000 caratteri";
    public static final String NOT_COMPLETED = "Puoi valutare solo i contenuti completati";

    /** Una recensione vuota equivale a nessuna recensione. */
    public RatingRequest {
        if (review != null && review.isBlank()) {
            review = null;
        } else if (review != null) {
            review = review.trim();
        }
    }
}
