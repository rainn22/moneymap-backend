package com.example.moneymap.features.alert.repository;

import com.example.moneymap.features.alert.entity.Alert;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserOrderByCreatedAtDesc(User user);

    Optional<Alert> findByIdAndUser(Long id, User user);

    boolean existsByBudgetAndThresholdPercentAndPeriodStartAndPeriodEnd(
            Budget budget,
            Integer thresholdPercent,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    );

    long countByUserAndIsReadFalse(User user);

    List<Alert> findAllByOrderByCreatedAtDesc();

    @Modifying
    void deleteByBudget(Budget budget);
}
