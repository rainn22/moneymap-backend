package com.example.moneymap.features.admin.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserSpendingResponse {
    private Long userId;
    private String username;
    private String userEmail;
    private Long categoryId;
    private String categoryName;
    private BigDecimal totalSpending;
    private long transactionCount;
}
