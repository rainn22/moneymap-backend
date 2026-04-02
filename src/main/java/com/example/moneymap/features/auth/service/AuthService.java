package com.example.moneymap.features.auth.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.moneymap.features.auth.dto.AuthResponse;
import com.example.moneymap.features.auth.dto.LoginRequest;
import com.example.moneymap.features.auth.dto.RegisterRequest;
import com.example.moneymap.features.auth.entity.RefreshToken;
import com.example.moneymap.features.auth.entity.VerificationToken;
import com.example.moneymap.features.auth.repository.VerificationTokenRepository;
import com.example.moneymap.features.user.dto.UserDto;
import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import com.example.moneymap.features.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    public String register(RegisterRequest request) {
        var existingUser = userRepository.findByEmail(request.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (user.isEnabled()) {
                throw new IllegalArgumentException("Email is already taken and verified.");
            }

            verificationTokenRepository.deleteByUser(user);
            log.info("Regenerating verification token for unverified user email={}", user.getEmail());
            return generateAndSendToken(user);
        }

        User newUser = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(false)
                .role(Role.USER)
                .build();

        userRepository.save(newUser);
        return generateAndSendToken(newUser);
    }

    private String generateAndSendToken(User user) {
        String verificationCode = UUID.randomUUID().toString();

        VerificationToken token = VerificationToken.builder()
                .token(verificationCode)
                .user(user)
                .expiryDate(LocalDateTime.now().plus(24, java.time.temporal.ChronoUnit.HOURS))
                .build();

        verificationTokenRepository.save(token);
        log.info("Generated verification token token={} userId={} email={} expiresAt={}",
                tokenFingerprint(verificationCode), user.getId(), user.getEmail(), token.getExpiryDate());
        emailService.sendVerificationEmail(user.getEmail(), verificationCode);

        return "Verification link sent! Please check your email.";
    }

    @Transactional
    public String verifyToken(String tokenStr) {
        String normalizedToken = normalizeToken(tokenStr);
        log.info("Received email verification request token={}", tokenFingerprint(normalizedToken));

        VerificationToken token = verificationTokenRepository.findByToken(normalizedToken)
                .orElseThrow(() -> {
                    log.warn("Email verification failed: token not found token={}", tokenFingerprint(normalizedToken));
                    return new IllegalArgumentException("Invalid verification token");
                });

        User user = token.getUser();

        if (token.isUsed() || user.isEnabled()) {
            if (!token.isUsed()) {
                token.setUsedAt(LocalDateTime.now());
                verificationTokenRepository.save(token);
            }

            log.info("Email verification already completed token={} userId={} email={}",
                    tokenFingerprint(normalizedToken), user.getId(), user.getEmail());
            return "Email already verified. You can login.";
        }

        if (token.isExpired()) {
            log.warn("Email verification failed: token expired token={} userId={} email={} expiredAt={}",
                    tokenFingerprint(normalizedToken), user.getId(), user.getEmail(), token.getExpiryDate());
            throw new IllegalArgumentException("Verification token has expired");
        }

        user.setEnabled(true);
        userRepository.save(user);
        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);
        log.info("Email verification succeeded token={} userId={} email={}",
                tokenFingerprint(normalizedToken), user.getId(), user.getEmail());

        return "Email verified successfully. You can now login.";
    }

    private String normalizeToken(String tokenStr) {
        if (tokenStr == null) {
            throw new IllegalArgumentException("Invalid verification token");
        }

        String normalizedToken = tokenStr.trim();
        if (normalizedToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid verification token");
        }

        return normalizedToken;
    }

    private String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "missing";
        }

        String normalizedToken = token.trim();
        int visibleChars = Math.min(6, normalizedToken.length());
        return normalizedToken.substring(normalizedToken.length() - visibleChars);
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled());
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Email not verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateToken(user);
        return accessToken; // temporary for compatibility (controller will be updated)
    }

    public AuthResponse loginWithRefresh(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Email not verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshToken refresh = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refresh.getToken(),
                jwtTokenProvider.getJwtExpirationSeconds(),
                toUserDto(user));
    }

    public AuthResponse refresh(String refreshTokenStr) {
        RefreshToken existing = refreshTokenService.findByToken(refreshTokenStr);
        if (existing == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        if (existing.isRevoked() || existing.getExpiryDate().isBefore(LocalDateTime.now())) {
            // potential reuse or expired — revoke all tokens for user as precaution
            refreshTokenService.revokeAllForUser(existing.getUser());
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        // rotate: revoke the old one and issue a new refresh token
        refreshTokenService.revoke(existing);
        RefreshToken newRefresh = refreshTokenService.createRefreshToken(existing.getUser());

        User user = existing.getUser();
        String accessToken = jwtTokenProvider.generateToken(user);
        return new AuthResponse(
                accessToken,
                newRefresh.getToken(),
                jwtTokenProvider.getJwtExpirationSeconds(),
                toUserDto(user));
    }

    public String logoutByRefresh(String refreshTokenStr) {
        RefreshToken existing = refreshTokenService.findByToken(refreshTokenStr);
        if (existing != null) {
            refreshTokenService.revoke(existing);
        }
        return "Logged out";
    }
}
