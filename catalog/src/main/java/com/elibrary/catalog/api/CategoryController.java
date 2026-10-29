package com.elibrary.catalog.api;

import com.elibrary.catalog.application.CatalogApplicationService;
import com.elibrary.catalog.domain.Category;
import com.elibrary.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 分类列表（匿名可访问）。 */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CatalogApplicationService catalog;

    public CategoryController(CatalogApplicationService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(HttpServletRequest request) {
        List<Map<String, Object>> data = catalog.categories().stream()
                .map(CategoryController::toMap)
                .toList();
        return ApiResponses.ok(data, request);
    }

    private static Map<String, Object> toMap(Category category) {
        return Map.of(
                "id", category.categoryId(),
                "name", category.name(),
                "status", category.status(),
                "sortOrder", category.sortOrder());
    }
}