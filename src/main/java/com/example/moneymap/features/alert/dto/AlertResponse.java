package com.example.moneymap.features.alert.dto;

import com.example.moneymap.features.alert.entity.AlertType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertResponse {
    private Long id;
    private Long budgetId;
    private AlertType alertType;
    private String message;
    private Integer thresholdPercent;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
