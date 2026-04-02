package com.example.moneymap.features.transaction.controller;

import com.example.moneymap.common.dto.ApiResponse;
import com.example.moneymap.features.transaction.dto.CreateTransactionRequest;
import com.example.moneymap.features.transaction.dto.TransactionListResponse;
import com.example.moneymap.features.transaction.dto.TransactionResponse;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ApiResponse<TransactionResponse> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.success("Transaction created successfully", transactionService.createTransaction(request));
    }

    @GetMapping
    public ApiResponse<TransactionListResponse> getTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(transactionService.getTransactions(search, type, categoryId, offset, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> getTransactionById(@PathVariable Long id) {
        return ApiResponse.success(transactionService.getTransactionById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionResponse> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ApiResponse.success("Transaction updated successfully", transactionService.updateTransaction(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ApiResponse.success("Transaction deleted successfully", null);
    }
}
