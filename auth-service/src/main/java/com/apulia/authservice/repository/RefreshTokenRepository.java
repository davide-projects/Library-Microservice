package com.apulia.authservice.repository;

import com.apulia.authservice.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    List<RefreshToken> findByUser_IdAndRevokedAtIsNull(Integer userId);
}
