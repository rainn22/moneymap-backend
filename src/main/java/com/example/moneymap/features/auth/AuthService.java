package com.example.moneymap.features.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.moneymap.features.auth.dto.AuthResponse;
import com.example.moneymap.features.auth.dto.LoginRequest;
import com.example.moneymap.features.auth.dto.RegisterRequest;
import com.example.moneymap.features.user.User;
import com.example.moneymap.features.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

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
            return generateAndSendToken(user);
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User newUser = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(false)
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
        emailService.sendVerificationEmail(user.getEmail(), verificationCode);

        return "Verification link sent! Please check your email.";
    }

    public String verifyToken(String tokenStr) {
        VerificationToken token = verificationTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = token.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        verificationTokenRepository.delete(token);

        return "Email verified successfully. You can now login.";
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getLogin())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username/email or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Email not verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("Invalid username/email or password");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        var refresh = refreshTokenService.createRefreshToken(user);
        return accessToken; // temporary for compatibility (controller will be updated)
    }

    public AuthResponse loginWithRefresh(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getLogin())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username/email or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Email not verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("Invalid username/email or password");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        RefreshToken refresh = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refresh.getToken(), jwtTokenProvider.getJwtExpirationSeconds());
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

        String accessToken = jwtTokenProvider.generateToken(existing.getUser().getUsername());
        return new AuthResponse(accessToken, newRefresh.getToken(), jwtTokenProvider.getJwtExpirationSeconds());
    }

    public String logoutByRefresh(String refreshTokenStr) {
        RefreshToken existing = refreshTokenService.findByToken(refreshTokenStr);
        if (existing != null) {
            refreshTokenService.revoke(existing);
        }
        return "Logged out";
    }
}
