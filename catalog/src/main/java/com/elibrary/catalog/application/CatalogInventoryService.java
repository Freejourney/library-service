package com.elibrary.catalog.application;

import com.elibrary.catalog.infrastructure.CatalogItemRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 库存调整服务，实现 lending 可调用的库存端口。 */
@Service
public class CatalogInventoryService implements CatalogInventoryPort {

    private final CatalogItemRepository repository;

    public CatalogInventoryService(CatalogItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean decrementCopies(UUID itemId) {
        return repository.decrementCopies(itemId);
    }

    @Override
    public boolean incrementCopies(UUID itemId) {
        return repository.incrementCopies(itemId);
    }
}