package com.example.moneymap.features.alert.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.dto.AlertResponse;
import com.example.moneymap.features.alert.entity.Alert;
import com.example.moneymap.features.alert.entity.AlertType;
import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.auth.service.EmailService;
import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.category.entity.CategoryGroupType;
import com.example.moneymap.features.category.entity.CategorySpendingType;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final List<Integer> DAILY_VARIABLE_ALERT_THRESHOLDS = List.of(50, 80, 90, 100);
    private static final List<Integer> MONTHLY_ALERT_THRESHOLDS = List.of(50, 80, 90);

    private final AlertRepository alertRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts() {
        User user = currentUserService.getCurrentUser();
        return alertRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public AlertResponse markAsRead(Long id) {
        User user = currentUserService.getCurrentUser();
        Alert alert = alertRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setIsRead(true);
        return mapToResponse(alertRepository.save(alert));
    }

    @Transactional
    public void refreshAlertsForExpenseTransaction(Transaction transaction) {
        if (transaction.getType() != TransactionType.EXPENSE) {
            return;
        }

        refreshAlertsForDate(
                transaction.getUser(),
                transaction.getCategory().getId(),
                transaction.getCategory().getGroupType(),
                transaction.getTransactionDate(),
                true
        );
    }

    @Transactional
    public void refreshAlertsForDate(
            User user,
            Long categoryId,
            CategoryGroupType groupType,
            LocalDate referenceDate,
            boolean sendNotifications
    ) {
        refreshAlertsForDateInternal(user, categoryId, groupType, referenceDate, sendNotifications);
    }

    @Transactional
    public void refreshAlertsForRemovedExpense(
            User user,
            Long categoryId,
            CategoryGroupType groupType,
            LocalDate referenceDate
    ) {
        refreshAlertsForDateInternal(user, categoryId, groupType, referenceDate, false);
    }

    private void refreshAlertsForDateInternal(
            User user,
            Long categoryId,
            CategoryGroupType groupType,
            LocalDate referenceDate,
            boolean sendNotifications
    ) {
        List<Budget> budgets = budgetRepository.findActiveBudgetsByUserAndDate(
                user,
                referenceDate
        );

        for (Budget budget : budgets) {
            if (!isExpenseBudget(budget) || !matchesAllocation(budget, categoryId, groupType)) {
                continue;
            }

            refreshMonthlyAlerts(user, budget, referenceDate, sendNotifications);
        }

        refreshDerivedDailyAlerts(user, budgets, referenceDate, sendNotifications);
    }

    private void refreshMonthlyAlerts(User user, Budget budget, LocalDate referenceDate, boolean sendNotifications) {
        AlertEvaluation evaluation = resolveMonthlyEvaluation(budget, referenceDate);
        if (evaluation == null) {
            return;
        }

        alertRepository.deleteByBudgetAndPeriodStartAndPeriodEnd(
                budget,
                evaluation.periodStart().atStartOfDay(),
                evaluation.periodEnd().atTime(23, 59, 59)
        );

        BigDecimal spentAmount = sumMonthlySpentAmount(budget, evaluation.periodStart(), evaluation.periodEnd());
        saveThresholdAlerts(
                user,
                budget,
                evaluation,
                spentAmount,
                MONTHLY_ALERT_THRESHOLDS,
                sendNotifications
        );
    }

    private void refreshDerivedDailyAlerts(User user, List<Budget> budgets, LocalDate referenceDate, boolean sendNotifications) {
        DailyTrackingSelection selection = resolveDailyTrackingSelection(budgets);
        if (selection.anchorBudget() == null) {
            return;
        }

        for (Budget trackedBudget : selection.trackedBudgets()) {
            alertRepository.deleteByBudgetAndPeriodStartAndPeriodEnd(
                    trackedBudget,
                    referenceDate.atStartOfDay(),
                    referenceDate.atTime(23, 59, 59)
            );
        }

        AlertEvaluation evaluation = resolveDailyVariableEvaluation(selection, referenceDate);
        if (evaluation == null) {
            return;
        }

        BigDecimal spentAmount = sumDailyTrackedAmount(user, selection, referenceDate);
        saveThresholdAlerts(
                user,
                selection.anchorBudget(),
                evaluation,
                spentAmount,
                DAILY_VARIABLE_ALERT_THRESHOLDS,
                sendNotifications
        );
    }

    private void saveThresholdAlerts(
            User user,
            Budget budget,
            AlertEvaluation evaluation,
            BigDecimal spentAmount,
            List<Integer> thresholds,
            boolean sendNotifications
    ) {
        if (spentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal usagePercent = resolveUsagePercent(spentAmount, evaluation.limitAmount());
        for (Integer threshold : thresholds) {
            if (usagePercent.compareTo(BigDecimal.valueOf(threshold)) >= 0) {
                Alert savedAlert = alertRepository.save(Alert.builder()
                        .user(user)
                        .budget(budget)
                        .alertType(evaluation.alertType())
                        .message(buildAlertMessage(budget, evaluation, threshold, spentAmount))
                        .thresholdPercent(threshold)
                        .periodStart(evaluation.periodStart().atStartOfDay())
                        .periodEnd(evaluation.periodEnd().atTime(23, 59, 59))
                        .isRead(false)
                        .build());

                if (sendNotifications && shouldSendEmailNotification(evaluation)) {
                    emailService.sendBudgetExceededAlert(
                            savedAlert.getUser().getEmail(),
                            savedAlert.getMessage()
                    );
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public long countUnreadAlerts(User user) {
        return alertRepository.countByUserAndIsReadFalse(user);
    }

    private boolean isExpenseBudget(Budget budget) {
        if (budget.getAllocationType() == BudgetAllocationType.SAVINGS) {
            return false;
        }
        return budget.getCategory() == null || budget.getCategory().getType() == TransactionType.EXPENSE;
    }

    private boolean supportsDailyVariableTracking(Budget budget) {
        if (budget.getPeriodType() != BudgetPeriodType.MONTHLY || budget.getAllocationType() == BudgetAllocationType.SAVINGS) {
            return false;
        }
        return budget.getCategory() != null && budget.getCategory().getSpendingType() == CategorySpendingType.VARIABLE;
    }

    private boolean matchesAllocation(Budget budget, Long categoryId, CategoryGroupType groupType) {
        if (budget.getCategory() == null) {
            return true;
        }
        return budget.getCategory().getId().equals(categoryId);
    }

    private AlertEvaluation resolveMonthlyEvaluation(Budget budget, LocalDate referenceDate) {
        if (budget.getPeriodType() != BudgetPeriodType.MONTHLY) {
            LocalDate periodStart = budget.getPeriodType() == BudgetPeriodType.DAILY ? referenceDate : referenceDate.minusDays(referenceDate.getDayOfWeek().getValue() - 1L);
            LocalDate periodEnd = budget.getPeriodType() == BudgetPeriodType.DAILY ? referenceDate : periodStart.plusDays(6);
            LocalDate rangeStart = periodStart.isBefore(budget.getStartDate()) ? budget.getStartDate() : periodStart;
            LocalDate rangeEnd = periodEnd.isAfter(budget.getEndDate()) ? budget.getEndDate() : periodEnd;
            AlertType alertType = budget.getPeriodType() == BudgetPeriodType.DAILY ? AlertType.DAILY : AlertType.WEEKLY;
            return new AlertEvaluation(rangeStart, rangeEnd, budget.getAmountLimit(), alertType, TrackingMode.DIRECT);
        }

        LocalDate monthStart = referenceDate.withDayOfMonth(1);
        LocalDate monthEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
        LocalDate rangeStart = monthStart.isBefore(budget.getStartDate()) ? budget.getStartDate() : monthStart;
        LocalDate rangeEnd = monthEnd.isAfter(budget.getEndDate()) ? budget.getEndDate() : monthEnd;
        return new AlertEvaluation(rangeStart, rangeEnd, budget.getAmountLimit(), AlertType.MONTHLY, TrackingMode.MONTHLY);
    }

    private AlertEvaluation resolveDailyVariableEvaluation(DailyTrackingSelection selection, LocalDate referenceDate) {
        Budget anchorBudget = selection.anchorBudget();
        if (anchorBudget == null || referenceDate.isBefore(anchorBudget.getStartDate()) || referenceDate.isAfter(anchorBudget.getEndDate())) {
            return null;
        }

        BigDecimal spentBeforeToday = sumDailyTrackingSpent(
                anchorBudget.getUser(),
                selection,
                anchorBudget.getStartDate(),
                referenceDate.minusDays(1)
        );
        BigDecimal remainingAmount = selection.totalAmountLimit()
                .subtract(spentBeforeToday)
                .max(BigDecimal.ZERO);
        long remainingDays = ChronoUnit.DAYS.between(referenceDate, anchorBudget.getEndDate()) + 1;
        if (remainingDays <= 0) {
            return null;
        }

        BigDecimal dailyLimit = remainingAmount.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : remainingAmount.divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP);

        return new AlertEvaluation(referenceDate, referenceDate, dailyLimit, AlertType.DAILY, TrackingMode.DAILY_VARIABLE);
    }

    private String buildAlertMessage(
            Budget budget,
            AlertEvaluation evaluation,
            Integer threshold,
            BigDecimal spentAmount
    ) {
        if (evaluation.trackingMode() == TrackingMode.DAILY_VARIABLE) {
            return "You have reached " + threshold + "% of your daily spending limit. Spent today: "
                    + spentAmount
                    + " of "
                    + evaluation.limitAmount();
        }

        String categoryText = resolveBudgetLabel(budget);
        if (evaluation.trackingMode() == TrackingMode.MONTHLY) {
            return "You have reached " + threshold + "% of your monthly "
                    + categoryText
                    + " budget. Spent: "
                    + spentAmount
                    + " of "
                    + evaluation.limitAmount();
        }

        return "You have reached " + threshold + "% of your "
                + categoryText + " "
                + budget.getPeriodType().name().toLowerCase()
                + " budget. Spent: " + spentAmount;
    }

    private String resolveBudgetLabel(Budget budget) {
        if (budget.getAllocationType() == BudgetAllocationType.SAVINGS) {
            return budget.getSavingGoal() == null ? "savings" : budget.getSavingGoal().getTitle();
        }
        return budget.getCategory() == null ? "overall" : budget.getCategory().getName();
    }

    private AlertResponse mapToResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .budgetId(alert.getBudget().getId())
                .alertType(alert.getAlertType())
                .message(alert.getMessage())
                .thresholdPercent(alert.getThresholdPercent())
                .isRead(alert.getIsRead())
                .createdAt(alert.getCreatedAt())
                .build();
    }

    private BigDecimal sumMonthlySpentAmount(Budget budget, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumExpenseForBudgetPeriod(
                budget.getUser(),
                budget.getCategory() == null ? null : budget.getCategory().getId(),
                startDate,
                endDate
        );
    }

    private BigDecimal sumDailyTrackedAmount(User user, DailyTrackingSelection selection, LocalDate referenceDate) {
        return sumDailyTrackingSpent(user, selection, referenceDate, referenceDate);
    }

    private BigDecimal sumDailyTrackingSpent(User user, DailyTrackingSelection selection, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            return BigDecimal.ZERO;
        }
        if (!selection.categoryIds().isEmpty()) {
            return transactionRepository.sumExpenseForBudgetPeriodByCategoryIds(
                user,
                selection.categoryIds(),
                startDate,
                endDate
            );
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveUsagePercent(BigDecimal spentAmount, BigDecimal limitAmount) {
        if (limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return spentAmount.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return spentAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(limitAmount, 2, RoundingMode.HALF_UP);
    }

    private boolean shouldSendEmailNotification(AlertEvaluation evaluation) {
        return evaluation.trackingMode() == TrackingMode.MONTHLY;
    }

    private DailyTrackingSelection resolveDailyTrackingSelection(List<Budget> budgets) {
        List<Budget> variableCategoryBudgets = budgets.stream()
                .filter(this::supportsDailyVariableTracking)
                .filter(budget -> budget.getAllocationType() == BudgetAllocationType.CATEGORY)
                .toList();
        Budget anchorBudget = variableCategoryBudgets.isEmpty() ? null : variableCategoryBudgets.get(0);
        return new DailyTrackingSelection(
                variableCategoryBudgets,
                anchorBudget,
                variableCategoryBudgets.stream()
                        .map(Budget::getAmountLimit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP),
                variableCategoryBudgets.stream()
                        .map(budget -> budget.getCategory().getId())
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private record AlertEvaluation(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal limitAmount,
            AlertType alertType,
            TrackingMode trackingMode
    ) {
    }

    private record DailyTrackingSelection(
            List<Budget> trackedBudgets,
            Budget anchorBudget,
            BigDecimal totalAmountLimit,
            Set<Long> categoryIds
    ) {
    }

    private enum TrackingMode {
        DIRECT,
        MONTHLY,
        DAILY_VARIABLE
    }
}
