package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateBudgetRequest {
    private BudgetAllocationType allocationType;
    private Long categoryId;
    private Long savingGoalId;

    @DecimalMin(value = "0.01", message = "Estimated monthly income must be greater than zero")
    private BigDecimal estimatedMonthlyIncome;

    private BudgetPeriodType periodType;

    @DecimalMin(value = "0.01", message = "Amount limit must be greater than zero")
    private BigDecimal amountLimit;

    @DecimalMin(value = "0.01", message = "Percentage must be greater than zero")
    @DecimalMax(value = "100.00", message = "Percentage must not exceed 100")
    private BigDecimal percentage;

    private LocalDate startDate;

    private LocalDate endDate;
}
