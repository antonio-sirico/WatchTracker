package it.watchtracker.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dati di registrazione con le regole di RF 0.1. */
public record RegisterRequest(
        @NotBlank(message = "Il nickname è obbligatorio")
        @Pattern(regexp = "^[A-Za-z0-9._]{3,20}$",
                 message = "Il nickname deve avere da 3 a 20 caratteri tra lettere, numeri, punto e trattino basso")
        String nickname,

        @NotBlank(message = "L'e-mail è obbligatoria")
        @Email(message = "Formato e-mail non valido")
        @Size(max = 254, message = "E-mail troppo lunga")
        String email,

        @NotBlank(message = "La password è obbligatoria")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                 message = "La password deve avere almeno 8 caratteri, con almeno una lettera e un numero")
        String password) {}
