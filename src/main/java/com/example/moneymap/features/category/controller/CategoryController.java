package com.example.moneymap.features.category.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.category.dto.CategoryResponse;
import com.example.moneymap.features.category.dto.CreateCategoryRequest;
import com.example.moneymap.features.category.service.CategoryService;
import com.example.moneymap.features.transaction.entity.TransactionType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success("Category created successfully", categoryService.createPersonalCategory(request));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(@RequestParam(required = false) TransactionType type) {
        return ApiResponse.success(categoryService.getCategories(type));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return ApiResponse.success("Category updated successfully", categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ApiResponse.success("Category deleted successfully", null);
    }
}
