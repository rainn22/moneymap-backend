package com.example.moneymap.features.admin.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.admin.dto.AdminAlertResponse;
import com.example.moneymap.features.admin.dto.AdminDashboardResponse;
import com.example.moneymap.features.admin.dto.AdminUserResponse;
import com.example.moneymap.features.admin.service.AdminService;
import com.example.moneymap.features.category.dto.CategoryResponse;
import com.example.moneymap.features.category.dto.CreateCategoryRequest;
import com.example.moneymap.features.category.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CategoryService categoryService;

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

    @PatchMapping("/users/{id}/reactivate")
    public ApiResponse<AdminUserResponse> reactivateUser(@PathVariable Long id) {
        return ApiResponse.success("User reactivated successfully", adminService.reactivateUser(id));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AdminAlertResponse>> getAlerts() {
        return ApiResponse.success(adminService.getAllAlerts());
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> getCategories(@RequestParam(required = false) com.example.moneymap.features.transaction.entity.TransactionType type) {
        List<CategoryResponse> categories = categoryService.getSharedCategories();
        if (type != null) {
            categories = categories.stream()
                    .filter(category -> category.getType() == type)
                    .toList();
        }
        return ApiResponse.success(categories);
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success("Category created successfully", categoryService.createSharedCategory(request));
    }

    @PatchMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return ApiResponse.success("Category updated successfully", categoryService.updateSharedCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteSharedCategory(id);
        return ApiResponse.success("Category deleted successfully", null);
    }
}
