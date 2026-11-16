package com.apulia.authservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dell'encoding delle password (A10): il bean BCrypt di {@link SecurityConfig}
 * deve produrre hash non reversibili e accettare solo la password originale.
 */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void encodedValueIsABcryptHashDifferentFromTheRawPassword() {
        String hash = encoder.encode("Admin123!");

        assertThat(hash)
                .isNotEqualTo("Admin123!")
                .startsWith("$2");
    }

    @Test
    void matchesOnlyTheOriginalPassword() {
        String hash = encoder.encode("Admin123!");

        assertThat(encoder.matches("Admin123!", hash)).isTrue();
        assertThat(encoder.matches("Admin123", hash)).isFalse();
        assertThat(encoder.matches("admin123!", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }

    @Test
    void sameRawPasswordProducesDifferentHashes() {
        String first = encoder.encode("Admin123!");
        String second = encoder.encode("Admin123!");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("Admin123!", first)).isTrue();
        assertThat(encoder.matches("Admin123!", second)).isTrue();
    }

    @Test
    void seedHashesMatchTheDocumentedDevelopmentPasswords() {
        // Hash presenti in V3__seed_data.sql. Se cambiano, login di sviluppo e test si rompono.
        assertThat(encoder.matches("Admin123!",
                "$2a$10$Z0yxvC67Dtlm8V3peNqkXe0XMMT9eHTGkoSWAPtebD2.P62GeOUpe")).isTrue();
        assertThat(encoder.matches("User123!",
                "$2a$10$ZUDPdSiUEkyDihosbmZtk.Vx5PW/xxDPA8AsOta96DFKddrehsvWC")).isTrue();
    }
}