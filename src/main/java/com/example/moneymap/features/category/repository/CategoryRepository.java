package com.example.moneymap.features.category.repository;

import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
            select c
            from Category c
            where c.user is null or c.user = :user
            order by c.name asc
            """)
    List<Category> findAccessibleCategories(@Param("user") User user);

    @Query("""
            select c
            from Category c
            where (c.user is null or c.user = :user)
              and c.type = :type
            order by c.name asc
            """)
    List<Category> findAccessibleCategoriesByType(@Param("user") User user, @Param("type") TransactionType type);

    @Query("""
            select c
            from Category c
            where lower(c.name) = lower(:name)
              and c.user is null
            """)
    Optional<Category> findDefaultByNameIgnoreCase(@Param("name") String name);

    @Query("""
            select c
            from Category c
            where lower(c.name) = lower(:name)
              and c.user = :user
            """)
    Optional<Category> findByNameIgnoreCaseAndUser(@Param("name") String name, @Param("user") User user);

    @Query("""
            select c
            from Category c
            where c.id = :id
              and (c.user is null or c.user = :user)
            """)
    Optional<Category> findAccessibleById(@Param("id") Long id, @Param("user") User user);
}
