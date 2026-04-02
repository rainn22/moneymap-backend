package com.example.moneymap.features.transaction.repository;

import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserOrderByTransactionDateDescCreatedAtDesc(User user);

    Optional<Transaction> findByIdAndUser(Long id, User user);

    List<Transaction> findTop5ByUserOrderByTransactionDateDescCreatedAtDesc(User user);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user = :user
              and t.type = :type
            """)
    BigDecimal sumAmountByUserAndType(@Param("user") User user, @Param("type") TransactionType type);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user = :user
              and t.type = :type
              and t.transactionDate between :startDate and :endDate
            """)
    BigDecimal sumAmountByUserAndTypeAndDateBetween(
            @Param("user") User user,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.user = :user
              and t.type = com.example.moneymap.features.transaction.entity.TransactionType.EXPENSE
              and t.transactionDate between :startDate and :endDate
              and (:categoryId is null or t.category.id = :categoryId)
            """)
    BigDecimal sumExpenseForBudgetPeriod(
            @Param("user") User user,
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    long countByType(TransactionType type);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.type = com.example.moneymap.features.transaction.entity.TransactionType.EXPENSE
            """)
    BigDecimal sumAllExpenses();

    @Query("""
            select count(distinct t.user.id)
            from Transaction t
            where t.type = com.example.moneymap.features.transaction.entity.TransactionType.EXPENSE
            """)
    long countDistinctUsersWithExpenses();

    @Query("""
            select t
            from Transaction t
            where (:userId is null or t.user.id = :userId)
              and (:categoryId is null or t.category.id = :categoryId)
            order by t.transactionDate desc, t.createdAt desc
            """)
    List<Transaction> findAllForAdmin(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    @Query(value = """
            select c.name
            from transactions t
            join categories c on c.id = t.category_id
            where t.type = 'EXPENSE'
            group by c.name
            order by sum(t.amount) desc
            limit 1
            """, nativeQuery = true)
    List<String> findTopSpentCategoryNames();
}
