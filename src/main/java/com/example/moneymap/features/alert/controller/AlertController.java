package com.example.moneymap.features.alert.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.alert.dto.AlertResponse;
import com.example.moneymap.features.alert.service.AlertService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ApiResponse<List<AlertResponse>> getAlerts() {
        return ApiResponse.success(alertService.getAlerts());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<AlertResponse> markAsRead(@PathVariable Long id) {
        return ApiResponse.success("Alert marked as read", alertService.markAsRead(id));
    }
}
