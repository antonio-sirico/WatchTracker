package it.watchtracker.user;

/** Utente come viene restituito al client. */
public record UserDto(Long id, String nickname, String email, boolean publicProfile) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getNickname(), u.getEmail(), u.isPublicProfile());
    }
}
