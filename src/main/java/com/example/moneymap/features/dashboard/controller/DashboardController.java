package com.example.moneymap.features.dashboard.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.dashboard.dto.DashboardResponse;
import com.example.moneymap.features.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.success(dashboardService.getDashboard());
    }
}
