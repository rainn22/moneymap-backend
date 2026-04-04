package com.example.moneymap.features.saving.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.saving.dto.AddMoneyRequest;
import com.example.moneymap.features.saving.dto.SavingGoalResponse;
import com.example.moneymap.features.saving.dto.CreateSavingGoalRequest;
import com.example.moneymap.features.saving.entity.SavingGoal;
import com.example.moneymap.features.saving.entity.SavingGoalStatus;
import com.example.moneymap.features.saving.repository.SavingGoalRepository;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.transaction.service.TransactionService;
import com.example.moneymap.features.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingGoalService {

    private final SavingGoalRepository savingGoalRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    @Transactional
    public SavingGoalResponse createSavingGoal(CreateSavingGoalRequest request) {
        User user = currentUserService.getCurrentUser();
        BigDecimal currentAmount = request.getCurrentAmount() == null ? BigDecimal.ZERO : request.getCurrentAmount();

        SavingGoal goal = SavingGoal.builder()
                .user(user)
                .title(request.getTitle().trim())
                .targetAmount(request.getTargetAmount())
                .currentAmount(currentAmount)
                .deadline(request.getDeadline())
                .status(determineStatus(currentAmount, request.getTargetAmount()))
                .build();

        return mapToResponse(savingGoalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public List<SavingGoalResponse> getSavingGoals() {
        User user = currentUserService.getCurrentUser();
        return savingGoalRepository.findByUserOrderByDeadlineAsc(user).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SavingGoalResponse addMoney(Long id, AddMoneyRequest request) {
        User user = currentUserService.getCurrentUser();
        SavingGoal goal = savingGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Saving goal not found"));

        goal.setCurrentAmount(goal.getCurrentAmount().add(request.getAmount()));
        goal.setStatus(determineStatus(goal.getCurrentAmount(), goal.getTargetAmount()));
        createSavingContributionTransaction(user, goal, request);

        return mapToResponse(savingGoalRepository.save(goal));
    }

    @Transactional
    public void deleteSavingGoal(Long id) {
        User user = currentUserService.getCurrentUser();
        SavingGoal goal = savingGoalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Saving goal not found"));
        transactionRepository.findBySavingGoalId(goal.getId())
                .forEach(transaction -> transaction.setSavingGoal(null));
        savingGoalRepository.delete(goal);
    }

    @Transactional(readOnly = true)
    public List<SavingGoal> getGoalsForUser(User user) {
        return savingGoalRepository.findByUserOrderByDeadlineAsc(user);
    }

    private void createSavingContributionTransaction(User user, SavingGoal goal, AddMoneyRequest request) {
        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            description = "Savings contribution: " + goal.getTitle();
        }

        LocalDate transactionDate = request.getTransactionDate() == null
                ? LocalDate.now()
                : request.getTransactionDate();

        transactionService.createSavingTransaction(user, goal, request.getAmount(), description, transactionDate);
    }

    private SavingGoalStatus determineStatus(BigDecimal currentAmount, BigDecimal targetAmount) {
        return currentAmount.compareTo(targetAmount) >= 0
                ? SavingGoalStatus.COMPLETED
                : SavingGoalStatus.IN_PROGRESS;
    }

    private SavingGoalResponse mapToResponse(SavingGoal goal) {
        BigDecimal progressPercent = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : goal.getCurrentAmount()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);

        return SavingGoalResponse.builder()
                .id(goal.getId())
                .title(goal.getTitle())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .progressPercent(progressPercent)
                .build();
    }
}
