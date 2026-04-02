package com.example.moneymap.features.admin.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalTransactions;
    private long totalBudgets;
    private long totalAlerts;
    private long totalSavingGoals;
    private String mostSpentCategory;
    private BigDecimal totalExpense;
    private BigDecimal averageUserSpending;
}
