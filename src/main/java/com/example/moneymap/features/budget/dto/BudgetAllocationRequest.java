package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.category.entity.CategoryGroupType;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BudgetAllocationRequest {
    private BudgetAllocationType allocationType;
    private Long categoryId;
    private CategoryGroupType groupType;
    private Long savingGoalId;

    @DecimalMin(value = "0.01", message = "Amount limit must be greater than zero")
    private BigDecimal amountLimit;

    @DecimalMin(value = "0.01", message = "Percentage must be greater than zero")
    private BigDecimal percentage;
}
