package it.watchtracker.library;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull(message = "Stato obbligatorio") WatchStatus status) {}
