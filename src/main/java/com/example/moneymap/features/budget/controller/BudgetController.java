package com.example.moneymap.features.budget.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.budget.dto.BudgetResponse;
import com.example.moneymap.features.budget.dto.BudgetSetupRequest;
import com.example.moneymap.features.budget.dto.BudgetSetupResponse;
import com.example.moneymap.features.budget.dto.CreateBudgetRequest;
import com.example.moneymap.features.budget.service.BudgetService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ApiResponse<BudgetResponse> createBudget(@Valid @RequestBody CreateBudgetRequest request) {
        return ApiResponse.success("Budget created successfully", budgetService.createBudget(request));
    }

    @PostMapping("/setup")
    public ApiResponse<BudgetSetupResponse> saveBudgetSetup(@Valid @RequestBody BudgetSetupRequest request) {
        return ApiResponse.success("Budget setup saved successfully", budgetService.saveBudgetSetup(request));
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> getBudgets() {
        return ApiResponse.success(budgetService.getBudgets());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBudget(@PathVariable Long id) {
        budgetService.deleteBudget(id);
        return ApiResponse.success("Budget deleted successfully", null);
    }
}
