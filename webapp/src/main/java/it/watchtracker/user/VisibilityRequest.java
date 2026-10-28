package it.watchtracker.user;

/** Tabella dei dati di UC-04: visibilità del profilo e sezioni da mostrare (RF 1.4). */
public record VisibilityRequest(
        boolean publicProfile,
        Boolean showLibrary,
        Boolean showStats,
        Boolean showReviews) {

    public static final String NO_SECTION = "Seleziona almeno una sezione da mostrare nel profilo pubblico";

    public boolean library() { return showLibrary == null || showLibrary; }
    public boolean stats() { return showStats == null || showStats; }
    public boolean reviews() { return showReviews == null || showReviews; }

    /** UC-04 E1: con il profilo pubblico serve almeno una sezione. */
    public boolean valid() {
        return !publicProfile || library() || stats() || reviews();
    }
}
