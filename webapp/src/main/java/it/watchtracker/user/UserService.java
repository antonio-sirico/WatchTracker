package it.watchtracker.user;

import it.watchtracker.common.ApiException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** UC-02: credenziali errate senza indicare quale campo è sbagliato (RF 1.1). */
    public static final String BAD_CREDENTIALS = "Nickname o password non corretti";

    private final UserRepository repository;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    /** RF 0.1 / UC-01: registrazione con nickname ed e-mail univoci e conferma della password. */
    @Transactional
    public User register(RegisterRequest request) {
        if (!request.passwordsMatch()) {
            throw ApiException.badRequest("Dati non validi", "confirmPassword", RegisterRequest.CONFIRM_MESSAGE);
        }
        String nickname = request.nickname().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByNicknameIgnoreCase(nickname)) {
            throw new ApiException(HttpStatus.CONFLICT, RegisterRequest.NICKNAME_TAKEN,
                    java.util.Map.of("nickname", RegisterRequest.NICKNAME_TAKEN));
        }
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, RegisterRequest.EMAIL_TAKEN,
                    java.util.Map.of("email", RegisterRequest.EMAIL_TAKEN));
        }
        return repository.save(new User(nickname, email, encoder.encode(request.password())));
    }

    /** RF 1.1: autenticazione con nickname e password. */
    @Transactional(readOnly = true)
    public User authenticate(String nickname, String password) {
        return repository.findByNicknameIgnoreCase(nickname.trim())
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS));
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Accesso richiesto"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByNickname(String nickname) {
        return repository.findByNicknameIgnoreCase(nickname == null ? "" : nickname.trim());
    }

    /** RF 1.4 / UC-04: visibilità del profilo e sezioni mostrate. */
    @Transactional
    public User setVisibility(User user, VisibilityRequest request) {
        if (!request.valid()) {
            throw ApiException.badRequest("Dati non validi", "sections", VisibilityRequest.NO_SECTION);
        }
        user.setPublicProfile(request.publicProfile());
        user.setShowLibrary(request.library());
        user.setShowStats(request.stats());
        user.setShowReviews(request.reviews());
        return repository.save(user);
    }

    /** Usato dall'importazione del backup per riportare le impostazioni salvate (RF 4.7). */
    @Transactional
    public User save(User user) {
        return repository.save(user);
    }
}
