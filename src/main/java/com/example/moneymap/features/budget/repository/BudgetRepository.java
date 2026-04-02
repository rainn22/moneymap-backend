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
