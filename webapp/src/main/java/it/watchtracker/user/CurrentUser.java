package it.watchtracker.user;

import it.watchtracker.common.ApiException;
import jakarta.servlet.http.HttpSession;
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
        Object id = session.getAttribute(SESSION_KEY);
        if (!(id instanceof Long userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Accesso richiesto");
        }
        return users.require(userId);
    }
}
