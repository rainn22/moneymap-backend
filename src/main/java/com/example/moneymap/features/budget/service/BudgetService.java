package com.example.moneymap.features.budget.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.service.CategoryService;
import com.example.moneymap.features.budget.dto.BudgetResponse;
import com.example.moneymap.features.budget.dto.CreateBudgetRequest;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final AlertRepository alertRepository;
    private final CurrentUserService currentUserService;
    private final CategoryService categoryService;

    @Transactional
    public BudgetResponse createBudget(CreateBudgetRequest request) {
        User user = currentUserService.getCurrentUser();
        validateBudgetRequest(request);
        Category category = request.getCategoryId() == null ? null : categoryService.getCategoryById(request.getCategoryId());

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .periodType(request.getPeriodType())
                .amountLimit(request.getAmountLimit())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        return mapToResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets() {
        User user = currentUserService.getCurrentUser();
        return budgetRepository.findByUserOrderByStartDateDesc(user).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteBudget(Long id) {
        User user = currentUserService.getCurrentUser();
        Budget budget = budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        alertRepository.deleteByBudget(budget);
        budgetRepository.delete(budget);
    }

    private void validateBudgetRequest(CreateBudgetRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("End date must be after or equal to start date");
        }
    }

    private BudgetResponse mapToResponse(Budget budget) {
        return BudgetResponse.builder()
                .id(budget.getId())
                .categoryId(budget.getCategory() == null ? null : budget.getCategory().getId())
                .categoryName(budget.getCategory() == null ? null : budget.getCategory().getName())
                .periodType(budget.getPeriodType())
                .amountLimit(budget.getAmountLimit())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .build();
    }
}
