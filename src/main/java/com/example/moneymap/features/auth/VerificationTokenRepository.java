package com.example.moneymap.features.auth;

import java.util.Optional;

import com.example.moneymap.features.user.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);

    @Transactional
    void deleteByUser(User user);
}
