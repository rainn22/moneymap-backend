package com.example.moneymap.features.admin.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.admin.dto.AdminAlertResponse;
import com.example.moneymap.features.admin.dto.AdminDashboardResponse;
import com.example.moneymap.features.admin.dto.AdminUserResponse;
import com.example.moneymap.features.admin.dto.AdminUserSpendingResponse;
import com.example.moneymap.features.admin.service.AdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(adminService.getDashboard());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> getUsers() {
        return ApiResponse.success(adminService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.success(adminService.getUserById(id));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ApiResponse<AdminUserResponse> deactivateUser(@PathVariable Long id) {
        return ApiResponse.success("User deactivated successfully", adminService.deactivateUser(id));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<AdminUserSpendingResponse>> getTransactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long categoryId
    ) {
        return ApiResponse.success(adminService.getUserSpendingSummaries(userId, categoryId));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AdminAlertResponse>> getAlerts() {
        return ApiResponse.success(adminService.getAllAlerts());
    }
}
