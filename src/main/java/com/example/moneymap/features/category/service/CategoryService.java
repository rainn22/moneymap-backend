package com.example.moneymap.features.category.service;

import com.example.moneymap.common.security.CurrentUserService;
import com.example.moneymap.features.category.dto.CategoryResponse;
import com.example.moneymap.features.category.dto.CreateCategoryRequest;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.repository.CategoryRepository;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        User user = currentUserService.getCurrentUser();
        String normalizedName = request.getName().trim();

        if (user.getRole() == Role.ADMIN) {
            if (categoryRepository.findDefaultByNameIgnoreCase(normalizedName).isPresent()) {
                throw new RuntimeException("Category name already exists");
            }
        } else if (categoryRepository.findByNameIgnoreCaseAndUser(normalizedName, user).isPresent()) {
            throw new RuntimeException("Category name already exists");
        }

        Category category = Category.builder()
                .name(normalizedName)
                .type(request.getType())
                .user(user.getRole() == Role.ADMIN ? null : user)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(TransactionType type) {
        User user = currentUserService.getCurrentUser();
        List<Category> categories = type == null
                ? categoryRepository.findAccessibleCategories(user)
                : categoryRepository.findAccessibleCategoriesByType(user, type);

        return categories.stream()
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
                .userId(category.getUser() == null ? null : category.getUser().getId())
                .isDefault(category.getUser() == null)
                .createdAt(category.getCreatedAt())
                .build();
    }
}
