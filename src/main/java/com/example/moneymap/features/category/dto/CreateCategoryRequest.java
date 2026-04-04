package com.example.moneymap.features.category.dto;

import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.category.entity.CategoryGroupType;
import com.example.moneymap.features.category.entity.CategorySpendingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    @NotNull(message = "Category type is required")
    private TransactionType type;

    private CategoryGroupType groupType;

    private CategorySpendingType spendingType;

    private Long defaultCategoryId;
}
