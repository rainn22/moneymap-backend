package com.example.moneymap.features.budget.dto;

import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BudgetResponse {
    private Long id;
    private BudgetAllocationType allocationType;
    private Long categoryId;
    private String categoryName;
    private Long savingGoalId;
    private String savingGoalTitle;
    private BudgetPeriodType periodType;
    private BigDecimal amountLimit;
    private BigDecimal percentage;
    private BigDecimal dailyRecommendedAmount;
    private BigDecimal weeklyRecommendedAmount;
    private LocalDate startDate;
    private LocalDate endDate;
}
