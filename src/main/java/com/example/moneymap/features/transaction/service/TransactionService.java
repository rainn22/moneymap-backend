package com.example.moneymap.features.transaction.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.service.AlertService;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.service.CategoryService;
import com.example.moneymap.features.saving.entity.SavingGoal;
import com.example.moneymap.features.saving.repository.SavingGoalRepository;
import com.example.moneymap.features.transaction.dto.CreateTransactionRequest;
import com.example.moneymap.features.transaction.dto.TransactionListResponse;
import com.example.moneymap.features.transaction.dto.TransactionResponse;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final AlertService alertService;
    private final CategoryService categoryService;
    private final SavingGoalRepository savingGoalRepository;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        User user = currentUserService.getCurrentUser();
        return createTransaction(user, request, true);
    }

    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(
            String search,
            TransactionType type,
            Long categoryId,
            int offset,
            int limit) {
        validatePagination(offset, limit);
        User user = currentUserService.getCurrentUser();
        Page<Transaction> transactions = transactionRepository.findAllByFilters(
                user,
                normalizeSearchPattern(search),
                type,
                categoryId,
                new OffsetBasedPageRequest(offset, limit));

        return TransactionListResponse.builder()
                .items(transactions.getContent().stream()
                        .map(this::mapToResponse)
                        .toList())
                .offset(offset)
                .limit(limit)
                .total(transactions.getTotalElements())
                .build();
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        User user = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, CreateTransactionRequest request) {
        User user = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        TransactionType previousType = transaction.getType();
        Long previousCategoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId();
        java.time.LocalDate previousDate = transaction.getTransactionDate();
        Category category = request.getCategoryId() == null ? null : categoryService.getCategoryById(request.getCategoryId());
        SavingGoal savingGoal = request.getSavingGoalId() == null
                ? null
                : savingGoalRepository.findByIdAndUser(request.getSavingGoalId(), user)
                        .orElseThrow(() -> new RuntimeException("Saving goal not found"));

        applyTransactionRequest(transaction, request, category, savingGoal);

        Transaction updatedTransaction = transactionRepository.save(transaction);

        if (previousType == TransactionType.EXPENSE) {
            alertService.refreshAlertsForRemovedExpense(user, previousCategoryId, previousDate);
        }

        if (updatedTransaction.getType() == TransactionType.EXPENSE) {
            alertService.refreshAlertsForExpenseTransaction(updatedTransaction);
        }

        return mapToResponse(updatedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        User user = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        TransactionType previousType = transaction.getType();
        Long previousCategoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId();
        java.time.LocalDate previousDate = transaction.getTransactionDate();
        transactionRepository.delete(transaction);

        if (previousType == TransactionType.EXPENSE) {
            alertService.refreshAlertsForRemovedExpense(user, previousCategoryId, previousDate);
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getLatestTransactions(User user) {
        return transactionRepository.findTop5ByUserOrderByTransactionDateDescCreatedAtDesc(user).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByType(User user, TransactionType type) {
        return transactionRepository.sumAmountByUserAndType(user, type);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByTypeForDateRange(User user, TransactionType type, java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        return transactionRepository.sumAmountByUserAndTypeAndDateBetween(user, type, startDate, endDate);
    }

    @Transactional
    public TransactionResponse createSavingTransaction(
            User user,
            SavingGoal savingGoal,
            BigDecimal amount,
            String description,
            java.time.LocalDate transactionDate
    ) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType(TransactionType.SAVING);
        request.setSavingGoalId(savingGoal.getId());
        request.setAmount(amount);
        request.setDescription(description);
        request.setTransactionDate(transactionDate);
        return createTransaction(user, request, false);
    }

    private String normalizeSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new RuntimeException("Offset must be greater than or equal to zero");
        }
        if (limit <= 0) {
            throw new RuntimeException("Limit must be greater than zero");
        }
    }

    private TransactionResponse createTransaction(User user, CreateTransactionRequest request, boolean refreshAlerts) {
        Category category = request.getCategoryId() == null ? null : categoryService.getCategoryById(request.getCategoryId());
        SavingGoal savingGoal = request.getSavingGoalId() == null
                ? null
                : savingGoalRepository.findByIdAndUser(request.getSavingGoalId(), user)
                        .orElseThrow(() -> new RuntimeException("Saving goal not found"));

        Transaction transaction = Transaction.builder()
                .user(user)
                .build();

        applyTransactionRequest(transaction, request, category, savingGoal);

        Transaction savedTransaction = transactionRepository.save(transaction);

        if (refreshAlerts && savedTransaction.getType() == TransactionType.EXPENSE) {
            alertService.refreshAlertsForExpenseTransaction(savedTransaction);
        }

        return mapToResponse(savedTransaction);
    }

    private void applyTransactionRequest(
            Transaction transaction,
            CreateTransactionRequest request,
            Category category,
            SavingGoal savingGoal
    ) {
        validateTransactionRequest(request, category, savingGoal);

        transaction.setCategory(category);
        transaction.setSavingGoal(savingGoal);
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getTransactionDate());
    }

    private void validateTransactionRequest(CreateTransactionRequest request, Category category, SavingGoal savingGoal) {
        if (request.getType() == TransactionType.SAVING) {
            if (savingGoal == null) {
                throw new RuntimeException("Saving goal is required for saving transactions");
            }
            if (category != null && category.getType() != TransactionType.SAVING) {
                throw new RuntimeException("Saving transactions can only use saving categories");
            }
            return;
        }

        if (category == null) {
            throw new RuntimeException("Category is required");
        }
        if (savingGoal != null) {
            throw new RuntimeException("Saving goal is only supported for saving transactions");
        }
        if (category.getType() != request.getType()) {
            throw new RuntimeException("Category type does not match transaction type");
        }
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUser().getId())
                .categoryId(transaction.getCategory() == null ? null : transaction.getCategory().getId())
                .categoryName(transaction.getCategory() == null ? null : transaction.getCategory().getName())
                .savingGoalId(transaction.getSavingGoal() == null ? null : transaction.getSavingGoal().getId())
                .savingGoalTitle(transaction.getSavingGoal() == null ? null : transaction.getSavingGoal().getTitle())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
