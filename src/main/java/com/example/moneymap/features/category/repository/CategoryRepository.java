package com.example.moneymap.features.category.repository;

import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.transaction.entity.TransactionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();

    List<Category> findByTypeOrderByNameAsc(TransactionType type);

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
