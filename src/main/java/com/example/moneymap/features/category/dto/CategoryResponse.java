package com.example.moneymap.features.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.moneymap.features.transaction.entity.TransactionType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private TransactionType type;
    private Long userId;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private LocalDateTime createdAt;
}
