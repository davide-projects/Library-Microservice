package com.apulia.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.apulia.authservice.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Cerca l'utente per username caricando subito i ruoli (D-A7-8).
     *
     * <p>{@code roles} e' una relazione LAZY: senza questo grafo il login
     * esploderebbe con {@code LazyInitializationException} appena il service
     * prova a leggere i nomi dei ruoli.</p>
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}