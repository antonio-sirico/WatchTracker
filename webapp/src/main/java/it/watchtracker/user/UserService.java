package it.watchtracker.user;

import it.watchtracker.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    /** RF 0.1: registrazione con nickname ed e-mail univoci. */
    @Transactional
    public User register(RegisterRequest request) {
        String nickname = request.nickname().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByNicknameIgnoreCase(nickname)) {
            throw new ApiException(HttpStatus.CONFLICT, "Nickname già in uso");
        }
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail già registrata");
        }
        return repository.save(new User(nickname, email, encoder.encode(request.password())));
    }

    /** RF 1.1: autenticazione con nickname e password. */
    @Transactional(readOnly = true)
    public User authenticate(String nickname, String password) {
        return repository.findByNicknameIgnoreCase(nickname.trim())
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Nickname o password errati"));
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Accesso richiesto"));
    }

    /** RF 1.4: rende il profilo pubblico o privato. */
    @Transactional
    public User setPublicProfile(User user, boolean publicProfile) {
        user.setPublicProfile(publicProfile);
        return repository.save(user);
    }
}
