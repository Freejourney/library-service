package com.elibrary.catalog.infrastructure;

import com.elibrary.catalog.application.CatalogReadPort;
import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.catalog.domain.Category;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CatalogItemRepository implements CatalogReadPort {

    private final CatalogMapper mapper;

    public CatalogItemRepository(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CatalogItem> lockById(UUID itemId) {
        return Optional.ofNullable(mapper.lockById(itemId)).map(ItemRow::toDomain);
    }

    @Override
    public List<Category> listActiveCategories() {
        return mapper.listActiveCategories().stream().map(CategoryRow::toDomain).toList();
    }

    @Override
    public Optional<CatalogItem> findAvailableById(UUID itemId) {
        return Optional.ofNullable(mapper.findAvailableById(itemId)).map(ItemRow::toDomain);
    }

    @Override
    public long countOnShelf(String query, UUID categoryId, String type, boolean availabilityOnly) {
        return mapper.countOnShelf(query, categoryId, type, availabilityOnly);
    }

    @Override
    public List<CatalogItem> searchOnShelf(String query, UUID categoryId, String type,
                                           boolean availabilityOnly, String sort,
                                           int limit, long offset) {
        return mapper.searchOnShelf(query, categoryId, type, availabilityOnly, sort, limit, offset)
                .stream().map(ItemRow::toDomain).toList();
    }

    /** 供 lending 模块在事务内调整可借数（借出 -1 / 归还 +1）。 */
    public boolean decrementCopies(UUID itemId) {
        return mapper.decrementCopies(itemId) == 1;
    }

    /** 供 lending 模块在事务内调整可借数（归还 +1）。 */
    public boolean incrementCopies(UUID itemId) {
        return mapper.incrementCopies(itemId) == 1;
    }
}