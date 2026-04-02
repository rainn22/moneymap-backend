package com.example.moneymap.features.category.service;

import com.example.moneymap.features.category.dto.CategoryResponse;
import com.example.moneymap.features.category.dto.CreateCategoryRequest;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.repository.CategoryRepository;
import com.example.moneymap.features.transaction.entity.TransactionType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new RuntimeException("Category name already exists");
        }

        Category category = Category.builder()
                .name(request.getName().trim())
                .type(request.getType())
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(TransactionType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.findByTypeOrderByNameAsc(type);

        return categories.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
