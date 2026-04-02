package com.example.moneymap.features.dashboard.dto;

import com.example.moneymap.features.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal remainingMonthlyBudget;
    private long unreadAlertsCount;
    private List<DashboardSavingGoalResponse> savingGoalProgress;
    private List<TransactionResponse> lastFiveTransactions;
}
