package com.apulia.authservice.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.apulia.authservice.security.TokenPair;
import com.apulia.authservice.service.AuthService;
import com.apulia.authservice.service.exception.MissingAccessTokenException;
import com.apulia.authservice.web.dto.LoginRequest;
import com.apulia.authservice.web.dto.LogoutRequest;
import com.apulia.authservice.web.dto.RefreshRequest;
import com.apulia.authservice.web.dto.TokenPairResponse;

import jakarta.validation.Valid;

/**
 * Endpoint di identita' (A7).
 *
 * <p>Solo traduzione HTTP: nessuna regola di business, nessun accesso al
 * database. La logica vive in {@link AuthService}.</p>
 *
 * <p>Le risposte che contengono token viaggiano con {@code Cache-Control: no-store}
 * e {@code Pragma: no-cache}: un access token in cache e' una sessione regalata.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = authService.login(request.username(), request.password());
        return withTokens(TokenPairResponse.from(pair));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair pair = authService.refresh(request.refreshToken());
        return withTokens(TokenPairResponse.from(pair));
    }

    /**
     * Revoca l'access token dell'header e, se presente, il refresh nel body.
     * Risponde 204 anche se i token erano gia' revocati (idempotenza, D-A7-5).
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                       @Valid @RequestBody(required = false) LogoutRequest request) {
        String accessToken = extractBearer(authorization);
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(accessToken, refreshToken);
    }

    private static ResponseEntity<TokenPairResponse> withTokens(TokenPairResponse body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    /** Estrae il token dallo schema Bearer, che per RFC 7235 non e' case-sensitive. */
    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new MissingAccessTokenException();
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new MissingAccessTokenException();
        }
        return token;
    }
}