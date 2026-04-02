package com.example.moneymap.features.admin.service;

import com.example.moneymap.features.admin.dto.AdminAlertResponse;
import com.example.moneymap.features.admin.dto.AdminDashboardResponse;
import com.example.moneymap.features.admin.dto.AdminUserResponse;
import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.saving.repository.SavingGoalRepository;
import com.example.moneymap.features.transaction.dto.TransactionResponse;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import com.example.moneymap.features.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final AlertRepository alertRepository;
    private final SavingGoalRepository savingGoalRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        BigDecimal totalExpense = transactionRepository.sumAllExpenses();
        long spendingUsers = transactionRepository.countDistinctUsersWithExpenses();
        BigDecimal averageUserSpending = spendingUsers == 0
                ? BigDecimal.ZERO
                : totalExpense.divide(BigDecimal.valueOf(spendingUsers), 2, RoundingMode.HALF_UP);

        List<String> mostSpentCategories = transactionRepository.findTopSpentCategoryNames();

        return AdminDashboardResponse.builder()
                .totalUsers(userRepository.count())
                .totalTransactions(transactionRepository.count())
                .totalBudgets(budgetRepository.count())
                .totalAlerts(alertRepository.count())
                .totalSavingGoals(savingGoalRepository.count())
                .mostSpentCategory(mostSpentCategories.isEmpty() ? null : mostSpentCategories.get(0))
                .totalExpense(totalExpense)
                .averageUserSpending(averageUserSpending)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapUser)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        User user = findUser(id);
        return mapUser(user);
    }

    @Transactional
    public AdminUserResponse deactivateUser(Long id) {
        User user = findUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new RuntimeException("Admin user cannot be deactivated");
        }
        user.setEnabled(false);
        return mapUser(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions(Long userId, Long categoryId) {
        return transactionRepository.findAllForAdmin(userId, categoryId).stream()
                .map(this::mapTransaction)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAlertResponse> getAllAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(alert -> AdminAlertResponse.builder()
                        .id(alert.getId())
                        .userId(alert.getUser().getId())
                        .userEmail(alert.getUser().getEmail())
                        .budgetId(alert.getBudget().getId())
                        .alertType(alert.getAlertType())
                        .message(alert.getMessage())
                        .thresholdPercent(alert.getThresholdPercent())
                        .isRead(alert.getIsRead())
                        .createdAt(alert.getCreatedAt())
                        .build())
                .toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminUserResponse mapUser(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }

    private TransactionResponse mapTransaction(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUser().getId())
                .categoryId(transaction.getCategory().getId())
                .categoryName(transaction.getCategory().getName())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
