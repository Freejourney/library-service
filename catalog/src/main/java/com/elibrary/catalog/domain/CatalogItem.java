package com.elibrary.catalog.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 目录条目。 */
public record CatalogItem(
        UUID itemId,
        String type,
        String title,
        String creator,
        UUID categoryId,
        String categoryName,
        String description,
        String publisher,
        LocalDate publicationDate,
        String languageCode,
        String identifierType,
        String identifierValue,
        String coverUrl,
        ShelfStatus shelfStatus,
        int totalCopies,
        int availableCopies,
        LocalDateTime publishedAt) {

    public boolean borrowable() {
        return shelfStatus.borrowable() && availableCopies > 0;
    }
}