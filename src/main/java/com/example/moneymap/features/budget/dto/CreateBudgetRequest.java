package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateBudgetRequest {
    private Long categoryId;

    @NotNull(message = "Period type is required")
    private BudgetPeriodType periodType;

    @NotNull(message = "Amount limit is required")
    @DecimalMin(value = "0.01", message = "Amount limit must be greater than zero")
    private BigDecimal amountLimit;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
