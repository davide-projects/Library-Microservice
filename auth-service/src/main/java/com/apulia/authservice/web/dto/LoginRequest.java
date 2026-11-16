package com.apulia.authservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo di {@code POST /auth/login}.
 *
 * <p>I limiti replicano quelli delle colonne ({@code username} 50 caratteri) e
 * fermano i payload abnormi prima di toccare il database.</p>
 */
public record LoginRequest(
        @NotBlank(message = "obbligatorio")
        @Size(max = 50, message = "non puo' superare 50 caratteri")
        String username,

        @NotBlank(message = "obbligatoria")
        @Size(max = 100, message = "non puo' superare 100 caratteri")
        String password) {
}