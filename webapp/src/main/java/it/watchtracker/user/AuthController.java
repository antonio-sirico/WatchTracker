package it.watchtracker.user;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final CurrentUser currentUser;

    public AuthController(UserService users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    /** UC-01: registrazione. L'account nasce con libreria vuota e profilo privato. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        User user = users.register(request);
        currentUser.login(session, user);
        return UserDto.from(user);
    }

    /** UC-02: accesso. */
    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        User user = users.authenticate(request.nickname(), request.password());
        currentUser.login(session, user);
        return UserDto.from(user);
    }

    /** UC-02 passo 5: disconnessione. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public UserDto me(HttpSession session) {
        return UserDto.from(currentUser.require(session));
    }

    /** UC-04: visibilità del profilo e sezioni condivise (RF 1.4, RF 4.1, RF 4.4). */
    @PatchMapping("/me/visibility")
    public UserDto visibility(@RequestBody VisibilityRequest request, HttpSession session) {
        User user = currentUser.require(session);
        return UserDto.from(users.setVisibility(user, request));
    }
}
