package com.example.moneymap.features.dashboard.dto;

import com.example.moneymap.features.saving.entity.SavingGoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSavingGoalResponse {
    private Long id;
    private String title;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal progressPercent;
    private LocalDate deadline;
    private SavingGoalStatus status;
}
