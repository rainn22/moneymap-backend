package com.example.moneymap.features.transaction.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.service.AlertService;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.service.CategoryService;
import com.example.moneymap.features.transaction.dto.CreateTransactionRequest;
import com.example.moneymap.features.transaction.dto.TransactionResponse;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

        if (category.getType() != request.getType()) {
            throw new RuntimeException("Category type does not match transaction type");
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .category(category)
                .amount(request.getAmount())
                .type(request.getType())
                .description(request.getDescription())
                .transactionDate(request.getTransactionDate())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        if (savedTransaction.getType() == TransactionType.EXPENSE) {
            alertService.checkAndCreateAlerts(savedTransaction);
        }

        return mapToResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions() {
        User user = currentUserService.getCurrentUser();
        return transactionRepository.findByUserOrderByTransactionDateDescCreatedAtDesc(user).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        User user = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return mapToResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        User user = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        transactionRepository.delete(transaction);
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
