package it.watchtracker.user;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Il nickname è obbligatorio") String nickname,
        @NotBlank(message = "La password è obbligatoria") String password) {}
