package com.example.moneymap.features.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public boolean isRevoked(String token) {
        if (token == null) return false;
        Optional<RevokedToken> r = revokedTokenRepository.findByToken(token);
        return r.isPresent();
    }

    @Transactional
    public void revokeToken(String token) {
        if (token == null) return;
        // If already revoked, nothing to do
        if (isRevoked(token)) return;

        java.util.Date expiry = jwtTokenProvider.getExpiryDateFromToken(token);
        LocalDateTime expiryDate = expiry == null ? LocalDateTime.now() : LocalDateTime.ofInstant(expiry.toInstant(), java.time.ZoneId.systemDefault());

        RevokedToken revoked = RevokedToken.builder()
                .token(token)
                .expiryDate(expiryDate)
                .build();

        revokedTokenRepository.save(revoked);
    }

    // periodic cleanup of expired revoked tokens
    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanupExpired() {
        revokedTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
