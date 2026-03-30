package com.example.moneymap.features.auth;

import org.springframework.stereotype.Service;

import com.example.moneymap.features.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtTokenProvider jwtTokenProvider;

    public String generateToken(User user) {
        return jwtTokenProvider.generateToken(user.getUsername());
    }

    public boolean validateToken(String token, User user) {
        String username = jwtTokenProvider.getUsernameFromToken(token);
        return username.equals(user.getUsername()) && jwtTokenProvider.validateToken(token);
    }

    public String getUsernameFromToken(String token) {
        return jwtTokenProvider.getUsernameFromToken(token);
    }
}
