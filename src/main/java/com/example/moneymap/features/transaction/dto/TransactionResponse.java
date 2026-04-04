package com.example.moneymap.features.transaction.dto;

import com.example.moneymap.features.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private Long savingGoalId;
    private String savingGoalTitle;
    private BigDecimal amount;
    private TransactionType type;
    private String description;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;
}
