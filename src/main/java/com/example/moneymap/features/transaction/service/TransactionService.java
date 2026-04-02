package com.example.moneymap.features.transaction.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.service.AlertService;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.service.CategoryService;
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

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        User user = currentUserService.getCurrentUser();
        Category category = categoryService.getCategoryById(request.getCategoryId());

        Transaction transaction = Transaction.builder()
                .user(user)
                .build();

        applyTransactionRequest(transaction, request, category);

        Transaction savedTransaction = transactionRepository.save(transaction);

        if (savedTransaction.getType() == TransactionType.EXPENSE) {
            alertService.refreshAlertsForExpenseTransaction(savedTransaction);
        }

        return mapToResponse(savedTransaction);
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
        Long previousCategoryId = transaction.getCategory().getId();
        java.time.LocalDate previousDate = transaction.getTransactionDate();
        Category category = categoryService.getCategoryById(request.getCategoryId());

        applyTransactionRequest(transaction, request, category);

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
        Long previousCategoryId = transaction.getCategory().getId();
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

    private void applyTransactionRequest(Transaction transaction, CreateTransactionRequest request, Category category) {
        if (category.getType() != request.getType()) {
            throw new RuntimeException("Category type does not match transaction type");
        }

        transaction.setCategory(category);
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getTransactionDate());
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
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
