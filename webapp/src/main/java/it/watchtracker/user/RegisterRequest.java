package it.watchtracker.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dati di registrazione con i vincoli e i messaggi della tabella dei dati di UC-01 (RF 0.1). */
public record RegisterRequest(
        @NotBlank(message = NICKNAME_MESSAGE)
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname,

        @NotBlank(message = EMAIL_MESSAGE)
        @Email(message = EMAIL_MESSAGE)
        @Pattern(regexp = ".+@.+\\..+", message = EMAIL_MESSAGE)
        @Size(max = 254, message = EMAIL_MESSAGE)
        String email,

        @NotBlank(message = PASSWORD_MESSAGE)
        @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = CONFIRM_MESSAGE)
        String confirmPassword) {

    public static final String NICKNAME_PATTERN = "^[A-Za-z0-9._]{3,20}$";
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    public static final String NICKNAME_MESSAGE =
            "Il nickname deve avere da 3 a 20 caratteri e può contenere solo lettere, numeri, punto e trattino basso";
    public static final String NICKNAME_TAKEN = "Nickname già in uso";
    public static final String EMAIL_MESSAGE = "Inserisci un indirizzo e-mail valido";
    public static final String EMAIL_TAKEN = "E-mail già registrata";
    public static final String PASSWORD_MESSAGE =
            "La password deve avere almeno 8 caratteri e contenere almeno una lettera e un numero";
    public static final String CONFIRM_MESSAGE = "Le due password non coincidono";

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
