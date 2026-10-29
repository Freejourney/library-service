package com.elibrary.catalog.infrastructure;

import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.catalog.domain.ShelfStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** catalog_items 表行映射。 */
public record ItemRow(
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
        String shelfStatus,
        int totalCopies,
        int availableCopies,
        LocalDateTime publishedAt) {

    public CatalogItem toDomain() {
        return new CatalogItem(
                itemId, type, title, creator, categoryId, categoryName,
                description, publisher, publicationDate, languageCode,
                identifierType, identifierValue, coverUrl,
                ShelfStatus.valueOf(shelfStatus), totalCopies, availableCopies, publishedAt);
    }
}