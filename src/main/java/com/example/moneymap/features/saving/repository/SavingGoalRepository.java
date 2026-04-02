package com.example.moneymap.features.saving.repository;

import com.example.moneymap.features.saving.entity.SavingGoal;
import com.example.moneymap.features.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    List<SavingGoal> findByUserOrderByDeadlineAsc(User user);

    Optional<SavingGoal> findByIdAndUser(Long id, User user);

    long countByUser(User user);
}
