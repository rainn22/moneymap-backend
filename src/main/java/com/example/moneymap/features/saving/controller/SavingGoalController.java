package com.example.moneymap.features.saving.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.saving.dto.AddMoneyRequest;
import com.example.moneymap.features.saving.dto.CreateSavingGoalRequest;
import com.example.moneymap.features.saving.dto.SavingGoalResponse;
import com.example.moneymap.features.saving.service.SavingGoalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saving-goals")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    @PostMapping
    public ApiResponse<SavingGoalResponse> createSavingGoal(@Valid @RequestBody CreateSavingGoalRequest request) {
        return ApiResponse.success("Saving goal created successfully", savingGoalService.createSavingGoal(request));
    }

    @GetMapping
    public ApiResponse<List<SavingGoalResponse>> getSavingGoals() {
        return ApiResponse.success(savingGoalService.getSavingGoals());
    }

    @PatchMapping("/{id}/add-money")
    public ApiResponse<SavingGoalResponse> addMoney(@PathVariable Long id, @Valid @RequestBody AddMoneyRequest request) {
        return ApiResponse.success("Money added successfully", savingGoalService.addMoney(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSavingGoal(@PathVariable Long id) {
        savingGoalService.deleteSavingGoal(id);
        return ApiResponse.success("Saving goal deleted successfully", null);
    }
}
