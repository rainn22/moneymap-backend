package com.example.moneymap.features.auth;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterRequest request) {
        try {
            return ApiResponse.success(authService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            return ApiResponse.success(authService.loginWithRefresh(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/verify")
    public ApiResponse<String> verify(@RequestParam String token) {
        try {
            return ApiResponse.success(authService.verifyToken(token));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestBody TokenRefreshRequest request) {
        try {
            if (request == null || request.getRefreshToken() == null) {
                return ApiResponse.error("Missing refreshToken in request body");
            }
            return ApiResponse.success(authService.logoutByRefresh(request.getRefreshToken()));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody TokenRefreshRequest request) {
        try {
            if (request == null || request.getRefreshToken() == null) {
                return ApiResponse.error("Missing refreshToken in request body");
            }
            return ApiResponse.success(authService.refresh(request.getRefreshToken()));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
