package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BudgetAllocationRequest {
    private BudgetAllocationType allocationType;
    private Long categoryId;
    private Long savingGoalId;

    @DecimalMin(value = "0.01", message = "Amount limit must be greater than zero")
    private BigDecimal amountLimit;

    @DecimalMin(value = "0.01", message = "Percentage must be greater than zero")
    private BigDecimal percentage;
}
