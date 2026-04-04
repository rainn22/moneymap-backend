package com.example.moneymap.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaCompatibilityConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void alignLegacySchema() {
        alignBudgetSchema();
        alignCategorySchema();
        alignTransactionSchema();
    }

    private void alignBudgetSchema() {
        jdbcTemplate.execute(
            "ALTER TABLE budgets " +
                "ADD COLUMN IF NOT EXISTS allocation_type VARCHAR(50)"
        );
        jdbcTemplate.execute(
            "ALTER TABLE budgets " +
                "ADD COLUMN IF NOT EXISTS percentage NUMERIC(5,2)"
        );
        jdbcTemplate.execute(
            "ALTER TABLE budgets " +
                "ADD COLUMN IF NOT EXISTS saving_goal_id BIGINT"
        );
        jdbcTemplate.execute(
            "UPDATE budgets SET allocation_type = 'CATEGORY' " +
                "WHERE allocation_type IS NULL"
        );
        try {
            jdbcTemplate.execute(
                "UPDATE budgets SET allocation_type = 'CATEGORY' " +
                    "WHERE allocation_type = 'GROUP'"
            );
        } catch (DataAccessException ignored) {
            // H2 enum columns reject comparisons against removed enum literals.
            // PostgreSQL legacy databases still benefit from the migration above.
        }
        jdbcTemplate.execute(
            "ALTER TABLE budgets " +
                "ALTER COLUMN allocation_type SET NOT NULL"
        );
    }

    private void alignCategorySchema() {
        jdbcTemplate.execute(
            "ALTER TABLE categories " +
                "ADD COLUMN IF NOT EXISTS spending_type VARCHAR(50)"
        );
        jdbcTemplate.execute(
            "ALTER TABLE categories " +
                "ADD COLUMN IF NOT EXISTS default_category_id BIGINT"
        );
        jdbcTemplate.execute(
            "UPDATE categories SET spending_type = 'VARIABLE' " +
                "WHERE spending_type IS NULL AND type = 'EXPENSE'"
        );
        jdbcTemplate.execute(
            "ALTER TABLE categories " +
                "DROP CONSTRAINT IF EXISTS categories_type_check"
        );
        jdbcTemplate.execute(
            "ALTER TABLE categories " +
                "ADD CONSTRAINT categories_type_check CHECK (type IN ('INCOME', 'EXPENSE', 'SAVING'))"
        );
    }

    private void alignTransactionSchema() {
        jdbcTemplate.execute(
            "ALTER TABLE transactions " +
                "ADD COLUMN IF NOT EXISTS saving_goal_id BIGINT"
        );
        jdbcTemplate.execute(
            "ALTER TABLE transactions " +
                "ALTER COLUMN category_id DROP NOT NULL"
        );
    }
}
