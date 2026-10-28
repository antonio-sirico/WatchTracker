package it.watchtracker.user;

import it.watchtracker.common.ApiException;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Risolve l'utente autenticato dalla sessione HTTP. */
@Component
public class CurrentUser {

    static final String SESSION_KEY = "userId";

    private final UserService users;

    public CurrentUser(UserService users) {
        this.users = users;
    }

    public void login(HttpSession session, User user) {
        session.setAttribute(SESSION_KEY, user.getId());
    }

    /** RF 0.7: le operazioni riservate richiedono un utente autenticato. */
    public User require(HttpSession session) {
        return find(session).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Accesso richiesto"));
    }

    /** Utente autenticato, se presente: per le pagine consultabili anche dal visitatore. */
    public Optional<User> find(HttpSession session) {
        Object id = session.getAttribute(SESSION_KEY);
        if (!(id instanceof Long userId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(users.require(userId));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }
}
