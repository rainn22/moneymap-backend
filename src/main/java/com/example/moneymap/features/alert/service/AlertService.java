package com.example.moneymap.features.alert.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.dto.AlertResponse;
import com.example.moneymap.features.alert.entity.Alert;
import com.example.moneymap.features.alert.entity.AlertType;
import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.auth.service.EmailService;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final List<Integer> ALERT_THRESHOLDS = List.of(80, 90, 100);

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
                transaction.getTransactionDate(),
                true
        );
    }

    @Transactional
    public void refreshAlertsForDate(User user, Long categoryId, LocalDate referenceDate, boolean sendNotifications) {
        refreshAlertsForDateInternal(user, categoryId, referenceDate, sendNotifications);
    }

    @Transactional
    public void refreshAlertsForRemovedExpense(User user, Long categoryId, LocalDate referenceDate) {
        refreshAlertsForDateInternal(user, categoryId, referenceDate, false);
    }

    private void refreshAlertsForDateInternal(User user, Long categoryId, LocalDate referenceDate, boolean sendNotifications) {
        List<Budget> budgets = budgetRepository.findActiveBudgetsByUserAndDate(
                user,
                referenceDate
        );

        for (Budget budget : budgets) {
            if (!matchesCategory(budget, categoryId)) {
                continue;
            }

            AlertEvaluation evaluation = resolveAlertEvaluation(budget, referenceDate);
            alertRepository.deleteByBudgetAndPeriodStartAndPeriodEnd(
                    budget,
                    evaluation.periodStart().atStartOfDay(),
                    evaluation.periodEnd().atTime(23, 59, 59)
            );

            BigDecimal spentAmount = transactionRepository.sumExpenseForBudgetPeriod(
                    budget.getUser(),
                    budget.getCategory() == null ? null : budget.getCategory().getId(),
                    evaluation.periodStart(),
                    evaluation.periodEnd()
            );

            if (spentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal usagePercent = spentAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(evaluation.limitAmount(), 2, RoundingMode.HALF_UP);

            for (Integer threshold : ALERT_THRESHOLDS) {
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

                    if (sendNotifications && threshold >= 100) {
                        emailService.sendBudgetExceededAlert(
                                savedAlert.getUser().getEmail(),
                                savedAlert.getMessage()
                        );
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public long countUnreadAlerts(User user) {
        return alertRepository.countByUserAndIsReadFalse(user);
    }

    private boolean matchesCategory(Budget budget, Long categoryId) {
        if (budget.getCategory() == null) {
            return true;
        }
        return budget.getCategory().getId().equals(categoryId);
    }

    private AlertEvaluation resolveAlertEvaluation(Budget budget, LocalDate referenceDate) {
        if (budget.getPeriodType() == BudgetPeriodType.MONTHLY) {
            long activeDays = ChronoUnit.DAYS.between(budget.getStartDate(), budget.getEndDate()) + 1;
            BigDecimal dailyLimit = budget.getAmountLimit()
                    .divide(BigDecimal.valueOf(activeDays), 2, RoundingMode.HALF_UP);
            return new AlertEvaluation(referenceDate, referenceDate, dailyLimit, AlertType.DAILY);
        }

        LocalDate periodStart = switch (budget.getPeriodType()) {
            case DAILY -> referenceDate;
            case WEEKLY -> referenceDate.with(DayOfWeek.MONDAY);
            case MONTHLY -> throw new IllegalStateException("Monthly budget should be resolved as daily alerts");
        };

        LocalDate periodEnd = switch (budget.getPeriodType()) {
            case DAILY -> referenceDate;
            case WEEKLY -> referenceDate.with(DayOfWeek.SUNDAY);
            case MONTHLY -> throw new IllegalStateException("Monthly budget should be resolved as daily alerts");
        };

        LocalDate rangeStart = periodStart.isBefore(budget.getStartDate()) ? budget.getStartDate() : periodStart;
        LocalDate rangeEnd = periodEnd.isAfter(budget.getEndDate()) ? budget.getEndDate() : periodEnd;
        AlertType alertType = switch (budget.getPeriodType()) {
            case DAILY -> AlertType.DAILY;
            case WEEKLY -> AlertType.WEEKLY;
            case MONTHLY -> throw new IllegalStateException("Monthly budget should be resolved as daily alerts");
        };
        return new AlertEvaluation(rangeStart, rangeEnd, budget.getAmountLimit(), alertType);
    }

    private String buildAlertMessage(
            Budget budget,
            AlertEvaluation evaluation,
            Integer threshold,
            BigDecimal spentAmount
    ) {
        String categoryText = budget.getCategory() == null ? "overall" : budget.getCategory().getName();
        if (budget.getPeriodType() == BudgetPeriodType.MONTHLY) {
            return "You have reached " + threshold + "% of your daily "
                    + categoryText
                    + " budget allowance from your monthly budget. Spent today: "
                    + spentAmount
                    + " of "
                    + evaluation.limitAmount();
        }

        return "You have reached " + threshold + "% of your "
                + categoryText + " "
                + budget.getPeriodType().name().toLowerCase()
                + " budget. Spent: " + spentAmount;
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

    private record AlertEvaluation(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal limitAmount,
            AlertType alertType
    ) {
    }
}
