package com.example.moneymap.features.budget.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BudgetSplitSuggestionResponse {
    private BigDecimal estimatedMonthlyIncome;
    private boolean ruleMode;
    private BigDecimal totalAllocatedAmount;
    private BigDecimal totalPercentage;
    private BigDecimal fixedTotal;
    private BigDecimal savingsTotal;
    private BigDecimal remainingAmount;
    private BigDecimal dailyBudget;
    private List<BudgetResponse> budgets;
}
