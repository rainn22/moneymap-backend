package com.example.moneymap.features.budget.repository;

import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserOrderByStartDateDesc(User user);

    @Query("""
            select b
            from Budget b
            where b.user = :user
              and b.periodType = :periodType
              and b.startDate = :startDate
              and b.endDate = :endDate
              and b.allocationType = :allocationType
              and ((:categoryId is null and b.category is null) or b.category.id = :categoryId)
              and ((:groupType is null and b.groupType is null) or b.groupType = :groupType)
              and ((:savingGoalId is null and b.savingGoal is null) or b.savingGoal.id = :savingGoalId)
            """)
    Optional<Budget> findMatchingBudget(
            @Param("user") User user,
            @Param("periodType") com.example.moneymap.features.budget.entity.BudgetPeriodType periodType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("allocationType") com.example.moneymap.features.budget.entity.BudgetAllocationType allocationType,
            @Param("categoryId") Long categoryId,
            @Param("groupType") com.example.moneymap.features.category.entity.CategoryGroupType groupType,
            @Param("savingGoalId") Long savingGoalId
    );

    Optional<Budget> findByIdAndUser(Long id, User user);

    @Query("""
            select b
            from Budget b
            where b.user = :user
              and :date between b.startDate and b.endDate
            order by b.category desc, b.startDate desc
            """)
    List<Budget> findActiveBudgetsByUserAndDate(@Param("user") User user, @Param("date") LocalDate date);

    long countByUser(User user);
}
