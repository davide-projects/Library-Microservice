package com.apulia.authservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo di {@code POST /auth/refresh}.
 *
 * <p>Il token arriva come stringa opaca: qui non si tenta di leggerlo, la
 * validazione vera e' del {@code TokenProvider}.</p>
 */
public record RefreshRequest(
        @NotBlank(message = "obbligatorio")
        @Size(max = 4096, message = "troppo lungo")
        String refreshToken) {
}