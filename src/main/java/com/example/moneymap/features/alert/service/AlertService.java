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
    public void checkAndCreateAlerts(Transaction transaction) {
        if (transaction.getType() != TransactionType.EXPENSE) {
            return;
        }

        List<Budget> budgets = budgetRepository.findActiveBudgetsByUserAndDate(
                transaction.getUser(),
                transaction.getTransactionDate()
        );

        for (Budget budget : budgets) {
            if (!matchesCategory(budget, transaction)) {
                continue;
            }

            BudgetPeriodRange periodRange = resolveBudgetPeriodRange(budget, transaction.getTransactionDate());
            BigDecimal spentAmount = transactionRepository.sumExpenseForBudgetPeriod(
                    budget.getUser(),
                    budget.getCategory() == null ? null : budget.getCategory().getId(),
                    periodRange.startDate(),
                    periodRange.endDate()
            );
            BigDecimal usagePercent = spentAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(budget.getAmountLimit(), 2, RoundingMode.HALF_UP);

            for (Integer threshold : ALERT_THRESHOLDS) {
                if (usagePercent.compareTo(BigDecimal.valueOf(threshold)) >= 0
                        && !alertRepository.existsByBudgetAndThresholdPercentAndPeriodStartAndPeriodEnd(
                                budget,
                                threshold,
                                periodRange.startDate().atStartOfDay(),
                                periodRange.endDate().atTime(23, 59, 59)
                        )) {
                    Alert savedAlert = alertRepository.save(Alert.builder()
                            .user(transaction.getUser())
                            .budget(budget)
                            .alertType(mapAlertType(budget.getPeriodType()))
                            .message(buildAlertMessage(budget, threshold, spentAmount))
                            .thresholdPercent(threshold)
                            .periodStart(periodRange.startDate().atStartOfDay())
                            .periodEnd(periodRange.endDate().atTime(23, 59, 59))
                            .isRead(false)
                            .build());

                    if (threshold >= 100) {
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

    private boolean matchesCategory(Budget budget, Transaction transaction) {
        if (budget.getCategory() == null) {
            return true;
        }
        return budget.getCategory().getId().equals(transaction.getCategory().getId());
    }

    private BudgetPeriodRange resolveBudgetPeriodRange(Budget budget, LocalDate referenceDate) {
        LocalDate periodStart = switch (budget.getPeriodType()) {
            case DAILY -> referenceDate;
            case WEEKLY -> referenceDate.with(DayOfWeek.MONDAY);
            case MONTHLY -> referenceDate.withDayOfMonth(1);
        };

        LocalDate periodEnd = switch (budget.getPeriodType()) {
            case DAILY -> referenceDate;
            case WEEKLY -> referenceDate.with(DayOfWeek.SUNDAY);
            case MONTHLY -> referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
        };

        LocalDate rangeStart = periodStart.isBefore(budget.getStartDate()) ? budget.getStartDate() : periodStart;
        LocalDate rangeEnd = periodEnd.isAfter(budget.getEndDate()) ? budget.getEndDate() : periodEnd;
        return new BudgetPeriodRange(rangeStart, rangeEnd);
    }

    private AlertType mapAlertType(BudgetPeriodType periodType) {
        return switch (periodType) {
            case DAILY -> AlertType.DAILY;
            case WEEKLY -> AlertType.WEEKLY;
            case MONTHLY -> AlertType.MONTHLY;
        };
    }

    private String buildAlertMessage(Budget budget, Integer threshold, BigDecimal spentAmount) {
        String categoryText = budget.getCategory() == null ? "overall" : budget.getCategory().getName();
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

    private record BudgetPeriodRange(LocalDate startDate, LocalDate endDate) {
    }
}
