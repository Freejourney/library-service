package com.elibrary.catalog.infrastructure;

import com.elibrary.catalog.domain.Category;

import java.util.UUID;

/** categories 表行映射。 */
public record CategoryRow(UUID categoryId, String name, String status, int sortOrder) {
    public Category toDomain() {
        return new Category(categoryId, name, status, sortOrder);
    }
}