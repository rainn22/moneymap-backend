package com.example.moneymap.features.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.auth.dto.AuthResponse;
import com.example.moneymap.features.auth.dto.LoginRequest;
import com.example.moneymap.features.auth.dto.RegisterRequest;
import com.example.moneymap.features.auth.dto.TokenRefreshRequest;
import com.example.moneymap.features.auth.service.AuthService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.loginWithRefresh(request));
    }

    @GetMapping("/verify")
    public ApiResponse<String> verify(
            @RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "verificationToken", required = false) String verificationToken) {
        String tokenValue = token != null ? token : verificationToken;
        return ApiResponse.success(authService.verifyToken(tokenValue));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestBody TokenRefreshRequest request) {
        if (request == null || request.getRefreshToken() == null) {
            throw new RuntimeException("Missing refreshToken in request body");
        }
        return ApiResponse.success(authService.logoutByRefresh(request.getRefreshToken()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody TokenRefreshRequest request) {
        if (request == null || request.getRefreshToken() == null) {
            throw new RuntimeException("Missing refreshToken in request body");
        }
        return ApiResponse.success(authService.refresh(request.getRefreshToken()));
    }
}
