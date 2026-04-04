package com.example.moneymap.features.dashboard.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.service.AlertService;
import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.dashboard.dto.DashboardResponse;
import com.example.moneymap.features.dashboard.dto.DashboardSavingGoalResponse;
import com.example.moneymap.features.saving.entity.SavingGoal;
import com.example.moneymap.features.saving.service.SavingGoalService;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.service.TransactionService;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;
    private final AlertService alertService;
    private final SavingGoalService savingGoalService;
    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        User user = currentUserService.getCurrentUser();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        BigDecimal totalMonthlyBudget = budgetRepository.findActiveBudgetsByUserAndDate(user, today).stream()
                .filter(budget -> budget.getPeriodType() == BudgetPeriodType.MONTHLY)
                .filter(budget -> budget.getAllocationType() != BudgetAllocationType.SAVINGS)
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpense = transactionService.getTotalAmountByTypeForDateRange(
                user,
                TransactionType.EXPENSE,
                monthStart,
                monthEnd
        );

        List<DashboardSavingGoalResponse> savingGoals = savingGoalService.getGoalsForUser(user).stream()
                .map(this::mapSavingGoal)
                .toList();

        return DashboardResponse.builder()
                .totalIncome(transactionService.getTotalAmountByType(user, TransactionType.INCOME))
                .totalExpense(transactionService.getTotalAmountByType(user, TransactionType.EXPENSE))
                .remainingMonthlyBudget(totalMonthlyBudget.subtract(monthlyExpense).max(BigDecimal.ZERO))
                .unreadAlertsCount(alertService.countUnreadAlerts(user))
                .savingGoalProgress(savingGoals)
                .lastFiveTransactions(transactionService.getLatestTransactions(user))
                .build();
    }

    private DashboardSavingGoalResponse mapSavingGoal(SavingGoal goal) {
        BigDecimal progressPercent = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : goal.getCurrentAmount()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);

        return DashboardSavingGoalResponse.builder()
                .id(goal.getId())
                .title(goal.getTitle())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .progressPercent(progressPercent)
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .build();
    }
}
