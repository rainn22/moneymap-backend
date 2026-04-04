package com.example.moneymap.features.budget.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.budget.dto.BudgetResponse;
import com.example.moneymap.features.budget.dto.BudgetAllocationRequest;
import com.example.moneymap.features.budget.dto.CreateBudgetRequest;
import com.example.moneymap.features.budget.dto.BudgetSetupRequest;
import com.example.moneymap.features.budget.dto.BudgetSetupResponse;
import com.example.moneymap.features.budget.dto.BudgetSplitSuggestionResponse;
import com.example.moneymap.features.budget.entity.BudgetAllocationType;
import com.example.moneymap.features.budget.entity.Budget;
import com.example.moneymap.features.budget.entity.BudgetPeriodType;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.entity.CategoryGroupType;
import com.example.moneymap.features.category.entity.CategorySpendingType;
import com.example.moneymap.features.category.service.CategoryService;
import com.example.moneymap.features.saving.entity.SavingGoal;
import com.example.moneymap.features.saving.repository.SavingGoalRepository;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final SavingGoalRepository savingGoalRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_NEEDS_PERCENT = BigDecimal.valueOf(50);
    private static final BigDecimal DEFAULT_WANTS_PERCENT = BigDecimal.valueOf(30);
    private static final BigDecimal DEFAULT_SAVINGS_PERCENT = BigDecimal.valueOf(20);

    @Transactional
    public BudgetResponse createBudget(CreateBudgetRequest request) {
        User user = currentUserService.getCurrentUser();
        BudgetInput input = normalizeCreateBudgetRequest(user, request);
        validateDateRange(input.periodType(), input.startDate(), input.endDate());
        Budget budget = upsertBudget(user, input);
        return mapToResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public BudgetSplitSuggestionResponse suggestBudgetSetup(BudgetSetupRequest request) {
        User user = currentUserService.getCurrentUser();
        validateDateRange(request.getPeriodType(), request.getStartDate(), request.getEndDate());
        List<BudgetInput> inputs = normalizeSetupAllocations(user, request);
        BudgetPlanSummary summary = summarizePlan(
                request.getEstimatedMonthlyIncome(),
                inputs,
                request.getStartDate(),
                request.getEndDate()
        );
        List<BudgetResponse> budgetResponses = inputs.stream()
                .map(input -> mapToResponse(input, inputs))
                .toList();
        return BudgetSplitSuggestionResponse.builder()
                .estimatedMonthlyIncome(request.getEstimatedMonthlyIncome())
                .ruleMode(isRuleMode(request))
                .totalAllocatedAmount(sumAmounts(inputs))
                .totalPercentage(sumPercentages(inputs))
                .fixedTotal(summary.fixedTotal())
                .savingsTotal(summary.savingsTotal())
                .remainingAmount(summary.remainingAmount())
                .dailyBudget(summary.dailyBudget())
                .budgets(budgetResponses)
                .build();
    }

    @Transactional
    public BudgetSetupResponse saveBudgetSetup(BudgetSetupRequest request) {
        User user = currentUserService.getCurrentUser();
        validateDateRange(request.getPeriodType(), request.getStartDate(), request.getEndDate());
        List<BudgetInput> inputs = normalizeSetupAllocations(user, request);
        BudgetPlanSummary summary = summarizePlan(
                request.getEstimatedMonthlyIncome(),
                inputs,
                request.getStartDate(),
                request.getEndDate()
        );

        List<BudgetResponse> responses = new ArrayList<>();
        for (BudgetInput input : inputs) {
            Budget budget = upsertBudget(user, input);
            budgetRepository.save(budget);
        }

        List<Budget> activeBudgets = budgetRepository.findByUserOrderByStartDateDesc(user);
        for (BudgetInput input : inputs) {
            Budget savedBudget = activeBudgets.stream()
                    .filter(budget -> budget.getPeriodType() == input.periodType())
                    .filter(budget -> budget.getStartDate().equals(input.startDate()))
                    .filter(budget -> budget.getEndDate().equals(input.endDate()))
                    .filter(budget -> budget.getAllocationType() == input.allocationType())
                    .filter(budget -> matchesInputTarget(budget, input))
                    .findFirst()
                    .orElseThrow();
            responses.add(mapToResponse(savedBudget));
        }

        return BudgetSetupResponse.builder()
                .estimatedMonthlyIncome(request.getEstimatedMonthlyIncome())
                .ruleMode(isRuleMode(request))
                .totalAllocatedAmount(sumAmounts(inputs))
                .totalPercentage(sumPercentages(inputs))
                .fixedTotal(summary.fixedTotal())
                .savingsTotal(summary.savingsTotal())
                .remainingAmount(summary.remainingAmount())
                .dailyBudget(summary.dailyBudget())
                .budgets(responses)
                .build();
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

    private void validateDateRange(BudgetPeriodType periodType, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (periodType == null) {
            throw new RuntimeException("Period type is required");
        }
        if (periodType != BudgetPeriodType.MONTHLY) {
            throw new RuntimeException("Only monthly budgets are supported for budget setup");
        }
        if (startDate == null) {
            throw new RuntimeException("Start date is required");
        }
        if (endDate == null) {
            throw new RuntimeException("End date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new RuntimeException("End date must be after or equal to start date");
        }
    }

    private BudgetResponse mapToResponse(Budget budget) {
        RecommendedTracking tracking = calculateRecommendedTracking(budget, LocalDate.now());
        return BudgetResponse.builder()
                .id(budget.getId())
                .allocationType(budget.getAllocationType())
                .categoryId(budget.getCategory() == null ? null : budget.getCategory().getId())
                .categoryName(budget.getCategory() == null ? null : budget.getCategory().getName())
                .groupType(budget.getGroupType())
                .savingGoalId(budget.getSavingGoal() == null ? null : budget.getSavingGoal().getId())
                .savingGoalTitle(budget.getSavingGoal() == null ? null : budget.getSavingGoal().getTitle())
                .periodType(budget.getPeriodType())
                .amountLimit(budget.getAmountLimit())
                .percentage(budget.getPercentage())
                .dailyRecommendedAmount(tracking.dailyRecommendedAmount())
                .weeklyRecommendedAmount(tracking.weeklyRecommendedAmount())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .build();
    }

    private BudgetResponse mapToResponse(BudgetInput input) {
        return mapToResponse(input, List.of(input));
    }

    private BudgetResponse mapToResponse(BudgetInput input, List<BudgetInput> allInputs) {
        RecommendedTracking tracking = calculateRecommendedTracking(input, allInputs);
        return BudgetResponse.builder()
                .allocationType(input.allocationType())
                .categoryId(input.category() == null ? null : input.category().getId())
                .categoryName(input.category() == null ? null : input.category().getName())
                .groupType(input.groupType())
                .savingGoalId(input.savingGoal() == null ? null : input.savingGoal().getId())
                .savingGoalTitle(input.savingGoal() == null ? null : input.savingGoal().getTitle())
                .periodType(input.periodType())
                .amountLimit(input.amountLimit())
                .percentage(input.percentage())
                .dailyRecommendedAmount(tracking.dailyRecommendedAmount())
                .weeklyRecommendedAmount(tracking.weeklyRecommendedAmount())
                .startDate(input.startDate())
                .endDate(input.endDate())
                .build();
    }

    private boolean matchesInputTarget(Budget budget, BudgetInput input) {
        Long budgetCategoryId = budget.getCategory() == null ? null : budget.getCategory().getId();
        Long inputCategoryId = input.category() == null ? null : input.category().getId();
        Long budgetSavingGoalId = budget.getSavingGoal() == null ? null : budget.getSavingGoal().getId();
        Long inputSavingGoalId = input.savingGoal() == null ? null : input.savingGoal().getId();
        return java.util.Objects.equals(budgetCategoryId, inputCategoryId)
                && budget.getGroupType() == input.groupType()
                && java.util.Objects.equals(budgetSavingGoalId, inputSavingGoalId);
    }

    private BudgetInput normalizeCreateBudgetRequest(User user, CreateBudgetRequest request) {
        BudgetAllocationType allocationType = resolveAllocationType(
                request.getAllocationType(),
                request.getCategoryId(),
                request.getGroupType(),
                request.getSavingGoalId()
        );
        Category category = resolveBudgetCategory(request.getCategoryId(), allocationType);
        SavingGoal savingGoal = resolveSavingGoal(user, request.getSavingGoalId(), allocationType);
        validateAllocationTarget(allocationType, category, request.getGroupType(), savingGoal);

        AmountAndPercentage normalized = normalizeAmountAndPercentage(
                request.getEstimatedMonthlyIncome(),
                request.getAmountLimit(),
                request.getPercentage()
        );

        return new BudgetInput(
                allocationType,
                category,
                request.getGroupType(),
                savingGoal,
                request.getPeriodType(),
                normalized.amountLimit(),
                normalized.percentage(),
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private List<BudgetInput> normalizeSetupAllocations(User user, BudgetSetupRequest request) {
        List<BudgetAllocationRequest> rawAllocations = request.getAllocations();
        if (rawAllocations == null || rawAllocations.isEmpty()) {
            rawAllocations = isRuleMode(request)
                    ? List.of(
                            buildDefaultAllocation(CategoryGroupType.NEEDS, DEFAULT_NEEDS_PERCENT),
                            buildDefaultAllocation(CategoryGroupType.WANTS, DEFAULT_WANTS_PERCENT),
                            buildSavingsAllocation(DEFAULT_SAVINGS_PERCENT)
                    )
                    : List.of();
        }

        List<BudgetInput> inputs = rawAllocations.stream()
                .map(allocation -> normalizeSetupAllocation(user, request, allocation))
                .toList();

        BigDecimal totalPercentage = sumPercentages(inputs);
        if (totalPercentage.compareTo(ONE_HUNDRED) > 0) {
            throw new RuntimeException("Total budget percentage must not exceed 100");
        }
        return inputs;
    }

    private BudgetInput normalizeSetupAllocation(User user, BudgetSetupRequest request, BudgetAllocationRequest allocation) {
        BudgetAllocationType allocationType = resolveAllocationType(
                allocation.getAllocationType(),
                allocation.getCategoryId(),
                allocation.getGroupType(),
                allocation.getSavingGoalId()
        );
        Category category = resolveBudgetCategory(allocation.getCategoryId(), allocationType);
        SavingGoal savingGoal = resolveSavingGoal(user, allocation.getSavingGoalId(), allocationType);
        validateAllocationTarget(allocationType, category, allocation.getGroupType(), savingGoal);

        AmountAndPercentage normalized = normalizeAmountAndPercentage(
                request.getEstimatedMonthlyIncome(),
                allocation.getAmountLimit(),
                allocation.getPercentage()
        );

        return new BudgetInput(
                allocationType,
                category,
                allocation.getGroupType(),
                savingGoal,
                request.getPeriodType(),
                normalized.amountLimit(),
                normalized.percentage(),
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private BudgetAllocationType resolveAllocationType(
            BudgetAllocationType requestedAllocationType,
            Long categoryId,
            CategoryGroupType groupType,
            Long savingGoalId
    ) {
        int selectedTargets = (categoryId != null ? 1 : 0) + (groupType != null ? 1 : 0) + (savingGoalId != null ? 1 : 0);
        if (selectedTargets > 1) {
            throw new RuntimeException("Each budget allocation must target only one of category, group, or saving goal");
        }
        if (requestedAllocationType != null) {
            return requestedAllocationType;
        }
        if (savingGoalId != null) {
            return BudgetAllocationType.SAVINGS;
        }
        if (groupType != null) {
            return BudgetAllocationType.GROUP;
        }
        return BudgetAllocationType.CATEGORY;
    }

    private Category resolveBudgetCategory(Long categoryId, BudgetAllocationType allocationType) {
        if (categoryId == null) {
            return null;
        }
        if (allocationType != BudgetAllocationType.CATEGORY) {
            throw new RuntimeException("Category budgets must use category allocation type");
        }
        Category category = categoryService.getCategoryById(categoryId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new RuntimeException("Budget categories must be expense categories");
        }
        return category;
    }

    private SavingGoal resolveSavingGoal(User user, Long savingGoalId, BudgetAllocationType allocationType) {
        if (savingGoalId == null) {
            return null;
        }
        if (allocationType != BudgetAllocationType.SAVINGS) {
            throw new RuntimeException("Saving goal allocations must use savings allocation type");
        }
        return savingGoalRepository.findByIdAndUser(savingGoalId, user)
                .orElseThrow(() -> new RuntimeException("Saving goal not found"));
    }

    private void validateAllocationTarget(
            BudgetAllocationType allocationType,
            Category category,
            CategoryGroupType groupType,
            SavingGoal savingGoal
    ) {
        if (allocationType == BudgetAllocationType.GROUP && groupType == null) {
            throw new RuntimeException("Group budget requires a group type");
        }
        if (allocationType == BudgetAllocationType.CATEGORY && groupType != null) {
            throw new RuntimeException("Category budget cannot use a group type");
        }
        if (allocationType == BudgetAllocationType.CATEGORY && savingGoal != null) {
            throw new RuntimeException("Category budget cannot use a saving goal");
        }
        if (allocationType == BudgetAllocationType.GROUP && (category != null || savingGoal != null)) {
            throw new RuntimeException("Group budget cannot target a category or saving goal");
        }
        if (allocationType == BudgetAllocationType.SAVINGS && (category != null || groupType != null)) {
            throw new RuntimeException("Savings budget cannot target a category or group");
        }
    }

    private AmountAndPercentage normalizeAmountAndPercentage(
            BigDecimal estimatedMonthlyIncome,
            BigDecimal amountLimit,
            BigDecimal percentage
    ) {
        if (amountLimit == null && percentage == null) {
            throw new RuntimeException("Amount limit or percentage is required");
        }

        if (amountLimit != null) {
            if (estimatedMonthlyIncome == null || estimatedMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
                return new AmountAndPercentage(scaleAmount(amountLimit), null);
            }
            BigDecimal normalizedPercentage = amountLimit
                    .multiply(ONE_HUNDRED)
                    .divide(estimatedMonthlyIncome, 2, RoundingMode.HALF_UP);
            validatePercentageLimit(normalizedPercentage);
            return new AmountAndPercentage(scaleAmount(amountLimit), scalePercentage(normalizedPercentage));
        }

        if (estimatedMonthlyIncome == null || estimatedMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Estimated monthly income is required when percentage is used");
        }
        BigDecimal normalizedAmount = estimatedMonthlyIncome
                .multiply(percentage)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        validatePercentageLimit(percentage);
        return new AmountAndPercentage(scaleAmount(normalizedAmount), scalePercentage(percentage));
    }

    private BigDecimal sumAmounts(List<BudgetInput> inputs) {
        return inputs.stream()
                .map(BudgetInput::amountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPercentages(List<BudgetInput> inputs) {
        return inputs.stream()
                .map(BudgetInput::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Budget upsertBudget(User user, BudgetInput input) {
        Budget budget = budgetRepository.findMatchingBudget(
                        user,
                        input.periodType(),
                        input.startDate(),
                        input.endDate(),
                        input.allocationType(),
                        input.category() == null ? null : input.category().getId(),
                        input.groupType(),
                        input.savingGoal() == null ? null : input.savingGoal().getId())
                .orElseGet(Budget::new);

        budget.setUser(user);
        budget.setAllocationType(input.allocationType());
        budget.setCategory(input.category());
        budget.setGroupType(input.groupType());
        budget.setSavingGoal(input.savingGoal());
        budget.setPeriodType(input.periodType());
        budget.setAmountLimit(scaleAmount(input.amountLimit()));
        budget.setPercentage(scalePercentage(input.percentage()));
        budget.setStartDate(input.startDate());
        budget.setEndDate(input.endDate());
        return budget;
    }

    private BudgetAllocationRequest buildDefaultAllocation(CategoryGroupType groupType, BigDecimal percentage) {
        BudgetAllocationRequest allocation = new BudgetAllocationRequest();
        allocation.setAllocationType(BudgetAllocationType.GROUP);
        allocation.setGroupType(groupType);
        allocation.setPercentage(percentage);
        return allocation;
    }

    private BudgetAllocationRequest buildSavingsAllocation(BigDecimal percentage) {
        BudgetAllocationRequest allocation = new BudgetAllocationRequest();
        allocation.setAllocationType(BudgetAllocationType.SAVINGS);
        allocation.setPercentage(percentage);
        return allocation;
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercentage(BigDecimal percentage) {
        if (percentage == null) {
            return null;
        }
        return percentage.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePercentageLimit(BigDecimal percentage) {
        if (percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new RuntimeException("Percentage must not exceed 100");
        }
    }

    private RecommendedTracking calculateRecommendedTracking(Budget budget, LocalDate today) {
        DailyTrackingSelection selection = resolveDailyTrackingSelection(
                budgetRepository.findActiveBudgetsByUserAndDate(budget.getUser(), today)
        );
        if (!selection.includes(budget) || today.isAfter(budget.getEndDate())) {
            return RecommendedTracking.empty();
        }

        LocalDate trackingStart = today.isBefore(budget.getStartDate()) ? budget.getStartDate() : today;
        BigDecimal spentBeforeToday = sumTrackedSpend(
                budget.getUser(),
                selection,
                budget.getStartDate(),
                trackingStart.minusDays(1)
        );
        BigDecimal remainingAmount = selection.totalAmountLimit()
                .subtract(spentBeforeToday)
                .max(BigDecimal.ZERO);

        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(trackingStart, budget.getEndDate()) + 1;
        if (remainingDays <= 0) {
            return RecommendedTracking.empty();
        }

        BigDecimal dailyRecommended = remainingAmount
                .divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP);
        BigDecimal weeklyRecommended = dailyRecommended
                .multiply(BigDecimal.valueOf(7))
                .min(remainingAmount)
                .setScale(2, RoundingMode.HALF_UP);

        return new RecommendedTracking(dailyRecommended, weeklyRecommended);
    }

    private RecommendedTracking calculateRecommendedTracking(BudgetInput input) {
        DailyTrackingInputSelection selection = resolveDailyTrackingInputSelection(List.of(input));
        if (!selection.includes(input)) {
            return RecommendedTracking.empty();
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(input.startDate(), input.endDate()) + 1;
        if (totalDays <= 0) {
            return RecommendedTracking.empty();
        }

        BigDecimal dailyRecommended = selection.totalAmountLimit()
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        BigDecimal weeklyRecommended = dailyRecommended
                .multiply(BigDecimal.valueOf(7))
                .min(selection.totalAmountLimit())
                .setScale(2, RoundingMode.HALF_UP);

        return new RecommendedTracking(dailyRecommended, weeklyRecommended);
    }

    private RecommendedTracking calculateRecommendedTracking(BudgetInput input, List<BudgetInput> allInputs) {
        DailyTrackingInputSelection selection = resolveDailyTrackingInputSelection(allInputs);
        if (!selection.includes(input)) {
            return RecommendedTracking.empty();
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(input.startDate(), input.endDate()) + 1;
        if (totalDays <= 0) {
            return RecommendedTracking.empty();
        }

        BigDecimal dailyRecommended = selection.totalAmountLimit()
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        BigDecimal weeklyRecommended = dailyRecommended
                .multiply(BigDecimal.valueOf(7))
                .min(selection.totalAmountLimit())
                .setScale(2, RoundingMode.HALF_UP);

        return new RecommendedTracking(dailyRecommended, weeklyRecommended);
    }

    private boolean supportsDerivedTracking(Budget budget) {
        if (budget.getPeriodType() != BudgetPeriodType.MONTHLY || budget.getAllocationType() == BudgetAllocationType.SAVINGS) {
            return false;
        }
        if (budget.getAllocationType() == BudgetAllocationType.GROUP) {
            return true;
        }
        return budget.getCategory() != null && budget.getCategory().getSpendingType() == CategorySpendingType.VARIABLE;
    }

    private boolean supportsDerivedTracking(BudgetInput input) {
        if (input.periodType() != BudgetPeriodType.MONTHLY || input.allocationType() == BudgetAllocationType.SAVINGS) {
            return false;
        }
        if (input.allocationType() == BudgetAllocationType.GROUP) {
            return true;
        }
        return input.category() != null && input.category().getSpendingType() == CategorySpendingType.VARIABLE;
    }

    private BigDecimal sumTrackedSpend(User user, DailyTrackingSelection selection, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            return BigDecimal.ZERO;
        }
        if (selection.usesGroupBudgets()) {
            return transactionRepository.sumExpenseForBudgetPeriodByGroupTypesAndSpendingType(
                    user,
                    selection.groupTypes(),
                    CategorySpendingType.VARIABLE,
                    startDate,
                    endDate
            );
        }
        if (!selection.categoryIds().isEmpty()) {
            return transactionRepository.sumExpenseForBudgetPeriodByCategoryIds(
                    user,
                    selection.categoryIds(),
                    startDate,
                    endDate
            );
        }
        return BigDecimal.ZERO;
    }

    private DailyTrackingSelection resolveDailyTrackingSelection(List<Budget> budgets) {
        List<Budget> groupBudgets = budgets.stream()
                .filter(this::supportsDerivedTracking)
                .filter(budget -> budget.getAllocationType() == BudgetAllocationType.GROUP)
                .toList();
        if (!groupBudgets.isEmpty()) {
            return new DailyTrackingSelection(
                    groupBudgets,
                    groupBudgets.stream()
                            .map(Budget::getAmountLimit)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP),
                    true,
                    groupBudgets.stream().map(Budget::getGroupType).collect(java.util.stream.Collectors.toSet()),
                    Set.of()
            );
        }

        List<Budget> variableCategoryBudgets = budgets.stream()
                .filter(this::supportsDerivedTracking)
                .filter(budget -> budget.getAllocationType() == BudgetAllocationType.CATEGORY)
                .toList();
        return new DailyTrackingSelection(
                variableCategoryBudgets,
                variableCategoryBudgets.stream()
                        .map(Budget::getAmountLimit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP),
                false,
                Set.of(),
                variableCategoryBudgets.stream()
                        .map(budget -> budget.getCategory().getId())
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private DailyTrackingInputSelection resolveDailyTrackingInputSelection(List<BudgetInput> inputs) {
        List<BudgetInput> groupInputs = inputs.stream()
                .filter(this::supportsDerivedTracking)
                .filter(input -> input.allocationType() == BudgetAllocationType.GROUP)
                .toList();
        if (!groupInputs.isEmpty()) {
            return new DailyTrackingInputSelection(
                    groupInputs,
                    groupInputs.stream()
                            .map(BudgetInput::amountLimit)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP)
            );
        }

        List<BudgetInput> variableCategoryInputs = inputs.stream()
                .filter(this::supportsDerivedTracking)
                .filter(input -> input.allocationType() == BudgetAllocationType.CATEGORY)
                .toList();
        return new DailyTrackingInputSelection(
                variableCategoryInputs,
                variableCategoryInputs.stream()
                        .map(BudgetInput::amountLimit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP)
        );
    }

    private boolean isRuleMode(BudgetSetupRequest request) {
        return request.getRuleMode() == null ? true : request.getRuleMode();
    }

    private BudgetPlanSummary summarizePlan(
            BigDecimal estimatedMonthlyIncome,
            List<BudgetInput> inputs,
            LocalDate planStartDate,
            LocalDate planEndDate
    ) {
        BigDecimal fixedTotal = inputs.stream()
                .filter(input -> input.allocationType() == BudgetAllocationType.CATEGORY)
                .filter(input -> input.category() != null && input.category().getSpendingType() == CategorySpendingType.FIXED)
                .map(BudgetInput::amountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal savingsTotal = inputs.stream()
                .filter(input -> input.allocationType() == BudgetAllocationType.SAVINGS)
                .map(BudgetInput::amountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = estimatedMonthlyIncome
                .subtract(fixedTotal)
                .subtract(savingsTotal)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(planStartDate, planEndDate) + 1;
        BigDecimal dailyBudget = totalDays <= 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : remainingAmount.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        return new BudgetPlanSummary(fixedTotal, savingsTotal, remainingAmount, dailyBudget);
    }

    private record AmountAndPercentage(BigDecimal amountLimit, BigDecimal percentage) {
    }

    private record RecommendedTracking(
            BigDecimal dailyRecommendedAmount,
            BigDecimal weeklyRecommendedAmount
    ) {
        private static RecommendedTracking empty() {
            return new RecommendedTracking(null, null);
        }
    }

    private record DailyTrackingSelection(
            List<Budget> budgets,
            BigDecimal totalAmountLimit,
            boolean usesGroupBudgets,
            Set<CategoryGroupType> groupTypes,
            Set<Long> categoryIds
    ) {
        private boolean includes(Budget budget) {
            return budgets.contains(budget);
        }
    }

    private record DailyTrackingInputSelection(
            List<BudgetInput> inputs,
            BigDecimal totalAmountLimit
    ) {
        private boolean includes(BudgetInput input) {
            return inputs.contains(input);
        }
    }

    private record BudgetPlanSummary(
            BigDecimal fixedTotal,
            BigDecimal savingsTotal,
            BigDecimal remainingAmount,
            BigDecimal dailyBudget
    ) {
    }

    private record BudgetInput(
            BudgetAllocationType allocationType,
            Category category,
            CategoryGroupType groupType,
            SavingGoal savingGoal,
            BudgetPeriodType periodType,
            BigDecimal amountLimit,
            BigDecimal percentage,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
    }
}
