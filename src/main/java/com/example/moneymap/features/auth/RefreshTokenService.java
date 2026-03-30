package com.example.moneymap.features.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.moneymap.features.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refreshExpiration}")
    private long refreshExpirationSeconds;

    public RefreshToken createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(refreshExpirationSeconds);

        RefreshToken rt = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiry)
                .revoked(false)
                .build();

        return refreshTokenRepository.save(rt);
    }

    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token).orElse(null);
    }

    @Transactional
    public void revoke(RefreshToken refreshToken) {
        if (refreshToken == null) return;
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        var list = refreshTokenRepository.findByUser(user);
        for (var t : list) {
            t.setRevoked(true);
        }
        refreshTokenRepository.saveAll(list);
    }

    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
