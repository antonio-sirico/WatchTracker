package it.watchtracker.catalog;

import java.time.LocalDate;

/**
 * Stato di prosecuzione di una serie TV o di un anime (RF 3.7, UC-07 E3).
 * <ul>
 *   <li>{@code TERMINATA}: non sono previsti nuovi episodi;</li>
 *   <li>{@code PROSSIMAMENTE}: nuovi episodi o stagioni previsti, data non nota;</li>
 *   <li>{@code DATA}: data prevista del prossimo episodio o della prossima stagione nota;</li>
 *   <li>{@code IGNOTA}: il catalogo non indica se la serie proseguirà.</li>
 * </ul>
 */
public record Continuation(Status status, LocalDate nextDate) {

    public enum Status { TERMINATA, PROSSIMAMENTE, DATA, IGNOTA }

    public static final Continuation TERMINATA = new Continuation(Status.TERMINATA, null);
    public static final Continuation PROSSIMAMENTE = new Continuation(Status.PROSSIMAMENTE, null);
    public static final Continuation IGNOTA = new Continuation(Status.IGNOTA, null);

    public static Continuation on(LocalDate date) {
        return date == null ? PROSSIMAMENTE : new Continuation(Status.DATA, date);
    }

    /** Etichetta in italiano da mostrare nella scheda. */
    public String label() {
        return switch (status) {
            case TERMINATA -> "Terminata";
            case PROSSIMAMENTE -> "Prossimamente";
            case DATA -> "Prossimo episodio: " + nextDate;
            case IGNOTA -> "Prosecuzione non nota";
        };
    }
}
