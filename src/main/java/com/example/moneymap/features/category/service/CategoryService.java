package com.example.moneymap.features.category.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.category.dto.CategoryResponse;
import com.example.moneymap.features.category.dto.CreateCategoryRequest;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.entity.CategoryGroupType;
import com.example.moneymap.features.category.entity.CategorySpendingType;
import com.example.moneymap.features.category.repository.CategoryRepository;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public CategoryResponse createPersonalCategory(CreateCategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        String normalizedName = request.getName().trim();

        if (user.getRole() == Role.ADMIN) {
            throw new RuntimeException("Admins must use the admin category endpoint");
        }

        if (request.getDefaultCategoryId() != null) {
            return upsertCategoryOverride(user, request.getDefaultCategoryId(), request, normalizedName);
        }

        if (categoryRepository.findByNameIgnoreCaseAndUser(normalizedName, user).isPresent()) {
            throw new RuntimeException("Category name already exists");
        }

        CategoryGroupType resolvedRuleType = resolveRuleType(request);
        validateCategoryAttributes(request.getType(), resolvedRuleType, request.getSpendingType());

        Category category = Category.builder()
                .name(normalizedName)
                .type(request.getType())
                .groupType(resolvedRuleType)
                .spendingType(resolveSpendingType(request.getType(), request.getSpendingType()))
                .user(user)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse createSharedCategory(CreateCategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        String normalizedName = request.getName().trim();

        if (user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can create shared categories");
        }

        if (categoryRepository.findDefaultByNameIgnoreCase(normalizedName).isPresent()) {
            throw new RuntimeException("Category name already exists");
        }

        CategoryGroupType resolvedRuleType = resolveRuleType(request);
        validateCategoryAttributes(request.getType(), resolvedRuleType, request.getSpendingType());

        Category category = Category.builder()
                .name(normalizedName)
                .type(request.getType())
                .groupType(resolvedRuleType)
                .spendingType(resolveSpendingType(request.getType(), request.getSpendingType()))
                .user(null)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(TransactionType type) {
        User user = currentUserService.getCurrentUser();
        List<Category> sharedCategories = type == null
                ? categoryRepository.findSharedCategories()
                : categoryRepository.findSharedCategories().stream()
                        .filter(category -> category.getType() == type)
                        .toList();
        List<Category> personalCategories = categoryRepository.findByUserOrderByNameAsc(user).stream()
                .filter(category -> type == null || category.getType() == type)
                .toList();

        Map<Long, Category> overridesByDefaultCategoryId = new LinkedHashMap<>();
        List<Category> personalCustomCategories = personalCategories.stream()
                .filter(category -> {
                    if (category.getDefaultCategory() == null) {
                        return true;
                    }
                    overridesByDefaultCategoryId.put(category.getDefaultCategory().getId(), category);
                    return false;
                })
                .toList();

        List<Category> categories = new java.util.ArrayList<>();
        for (Category sharedCategory : sharedCategories) {
            categories.add(overridesByDefaultCategoryId.getOrDefault(sharedCategory.getId(), sharedCategory));
        }
        categories.addAll(personalCustomCategories);

        return categories.stream()
                .sorted(java.util.Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getSharedCategories() {
        User user = currentUserService.getCurrentUser();
        if (user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can view shared categories");
        }

        return categoryRepository.findSharedCategories().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteCategory(Long id) {
        User user = currentUserService.getCurrentUser();
        Category category = categoryRepository.findAccessibleById(id, user)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (category.getUser() == null && user.getRole() != Role.ADMIN) {
            throw new RuntimeException("You cannot delete a default category");
        }

        categoryRepository.delete(category);
    }

    @Transactional
    public void deleteSharedCategory(Long id) {
        User user = currentUserService.getCurrentUser();
        if (user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can delete shared categories");
        }

        Category category = categoryRepository.findSharedById(id)
                .orElseThrow(() -> new RuntimeException("Shared category not found"));

        categoryRepository.deleteAll(categoryRepository.findByDefaultCategoryId(id));
        categoryRepository.delete(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        Category category = categoryRepository.findAccessibleById(id, user)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        String normalizedName = request.getName().trim();

        if (category.getUser() == null) {
            return upsertCategoryOverride(user, category.getId(), request, normalizedName);
        }

        if (category.getDefaultCategory() == null
                && categoryRepository.findByNameIgnoreCaseAndUser(normalizedName, user)
                        .filter(existing -> !existing.getId().equals(category.getId()))
                        .isPresent()) {
            throw new RuntimeException("Category name already exists");
        }

        return mapToResponse(updateCategoryEntity(category, request, normalizedName));
    }

    @Transactional
    public CategoryResponse updateSharedCategory(Long id, CreateCategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        if (user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admins can update shared categories");
        }

        Category category = categoryRepository.findSharedById(id)
                .orElseThrow(() -> new RuntimeException("Shared category not found"));
        String normalizedName = request.getName().trim();

        if (categoryRepository.findDefaultByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(category.getId()))
                .isPresent()) {
            throw new RuntimeException("Category name already exists");
        }

        return mapToResponse(updateCategoryEntity(category, request, normalizedName));
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        User user = currentUserService.getCurrentUser();
        return categoryRepository.findAccessibleById(id, user)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .groupType(category.getGroupType())
                .spendingType(category.getSpendingType())
                .userId(category.getUser() == null ? null : category.getUser().getId())
                .defaultCategoryId(category.getDefaultCategory() == null ? null : category.getDefaultCategory().getId())
                .override(category.getDefaultCategory() != null)
                .isDefault(category.getUser() == null)
                .createdAt(category.getCreatedAt())
                .build();
    }

    private void validateCategoryAttributes(
            TransactionType type,
            CategoryGroupType groupType,
            CategorySpendingType spendingType
    ) {
        if ((groupType != null || spendingType != null) && type != TransactionType.EXPENSE) {
            throw new RuntimeException("Category group type and spending type are only supported for expense categories");
        }
    }

    private CategorySpendingType resolveSpendingType(TransactionType type, CategorySpendingType spendingType) {
        if (type != TransactionType.EXPENSE) {
            return null;
        }
        return spendingType == null ? CategorySpendingType.VARIABLE : spendingType;
    }

    private CategoryGroupType resolveRuleType(CreateCategoryRequest request) {
        return request.getGroupType();
    }

    private CategoryResponse upsertCategoryOverride(
            User user,
            Long defaultCategoryId,
            CreateCategoryRequest request,
            String normalizedName
    ) {
        Category sharedCategory = categoryRepository.findSharedById(defaultCategoryId)
                .orElseThrow(() -> new RuntimeException("Default category not found"));

        Category override = categoryRepository.findOverrideByUserAndDefaultCategoryId(user, defaultCategoryId)
                .orElseGet(Category::new);
        override.setUser(user);
        override.setDefaultCategory(sharedCategory);

        return mapToResponse(updateCategoryEntity(override, request, normalizedName));
    }

    private Category updateCategoryEntity(Category category, CreateCategoryRequest request, String normalizedName) {
        CategoryGroupType resolvedRuleType = resolveRuleType(request);
        validateCategoryAttributes(request.getType(), resolvedRuleType, request.getSpendingType());

        category.setName(normalizedName);
        category.setType(request.getType());
        category.setGroupType(resolvedRuleType);
        category.setSpendingType(resolveSpendingType(request.getType(), request.getSpendingType()));
        return categoryRepository.save(category);
    }
}
