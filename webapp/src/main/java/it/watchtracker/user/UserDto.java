package it.watchtracker.user;

/** Utente come viene restituito al client, con le impostazioni di visibilità (RF 1.4). */
public record UserDto(
        Long id,
        String nickname,
        String email,
        boolean publicProfile,
        boolean showLibrary,
        boolean showStats,
        boolean showReviews,
        /** RF 4.1: percorso del profilo pubblico (relativo alla radice dell'applicazione). */
        String profilePath) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getNickname(), u.getEmail(), u.isPublicProfile(),
                u.isShowLibrary(), u.isShowStats(), u.isShowReviews(), profilePath(u));
    }

    public static String profilePath(User u) {
        return "/#/u/" + u.getNickname();
    }
}
