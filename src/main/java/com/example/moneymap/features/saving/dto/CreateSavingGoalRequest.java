package com.example.moneymap.features.saving.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateSavingGoalRequest {
    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be greater than zero")
    private BigDecimal targetAmount;

    @PositiveOrZero(message = "Current amount cannot be negative")
    private BigDecimal currentAmount;

    @NotNull(message = "Deadline is required")
    private LocalDate deadline;
}
