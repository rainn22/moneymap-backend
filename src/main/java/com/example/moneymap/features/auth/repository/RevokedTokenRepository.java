package com.example.moneymap.features.auth.repository;

import com.example.moneymap.features.auth.entity.RevokedToken;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    Optional<RevokedToken> findByToken(String token);
    void deleteByExpiryDateBefore(java.time.LocalDateTime dateTime);
}
