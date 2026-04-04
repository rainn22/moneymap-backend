package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class BudgetSetupRequest {
    @NotNull(message = "Estimated monthly income is required")
    @DecimalMin(value = "0.01", message = "Estimated monthly income must be greater than zero")
    private BigDecimal estimatedMonthlyIncome;

    @NotNull(message = "Period type is required")
    private BudgetPeriodType periodType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Boolean ruleMode;

    @Valid
    private List<BudgetAllocationRequest> allocations;
}
