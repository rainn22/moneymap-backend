package com.example.moneymap.features.transaction.repository;

import com.example.moneymap.features.admin.dto.AdminUserSpendingResponse;
import com.example.moneymap.features.transaction.entity.Transaction;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserOrderByTransactionDateDescCreatedAtDesc(User user);

    @Query(value = """
            select t
            from Transaction t
            where t.user = :user
              and (:type is null or t.type = :type)
              and (:categoryId is null or t.category.id = :categoryId)
              and (
                    :searchPattern is null
                    or (t.description is not null and lower(t.description) like :searchPattern)
                    or lower(t.category.name) like :searchPattern
                  )
            order by t.transactionDate desc, t.createdAt desc
            """,
            countQuery = """
                    select count(t)
                    from Transaction t
                    where t.user = :user
                      and (:type is null or t.type = :type)
                      and (:categoryId is null or t.category.id = :categoryId)
                      and (
                            :searchPattern is null
                            or (t.description is not null and lower(t.description) like :searchPattern)
                            or lower(t.category.name) like :searchPattern
                          )
                    """)
    Page<Transaction> findAllByFilters(
            @Param("user") User user,
            @Param("searchPattern") String searchPattern,
            @Param("type") TransactionType type,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

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
            select new com.example.moneymap.features.admin.dto.AdminUserSpendingResponse(
                t.user.id,
                t.user.username,
                t.user.email,
                t.category.id,
                t.category.name,
                coalesce(sum(t.amount), 0),
                count(t.id)
            )
            from Transaction t
            where t.type = com.example.moneymap.features.transaction.entity.TransactionType.EXPENSE
              and (:userId is null or t.user.id = :userId)
              and (:categoryId is null or t.category.id = :categoryId)
            group by t.user.id, t.user.username, t.user.email, t.category.id, t.category.name
            order by coalesce(sum(t.amount), 0) desc, t.user.id asc, t.category.name asc
            """)
    List<AdminUserSpendingResponse> findSpendingSummariesForAdmin(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId
    );

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
