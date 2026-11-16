package com.apulia.authservice.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo facoltativo di {@code POST /auth/logout}.
 *
 * <p>Il refresh non e' obbligatorio: il logout deve riuscire anche quando il
 * client ha gia' perso il token o chiama solo per invalidare l'access.</p>
 */
public record LogoutRequest(
        @Size(max = 4096, message = "troppo lungo")
        String refreshToken) {
}