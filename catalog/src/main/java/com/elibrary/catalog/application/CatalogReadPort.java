package com.elibrary.catalog.application;

import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.catalog.domain.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** catalog 模块对外提供的读取接口，供 lending 等模块调用（不得直接访问对方 Repository）。 */
public interface CatalogReadPort {

    /** 行锁读取条目，借阅/归还事务内使用。已下架/移除的条目也会返回。 */
    Optional<CatalogItem> lockById(UUID itemId);

    List<Category> listActiveCategories();

    /** 上架条目详情，下架/移除/不存在返回 empty。 */
    Optional<CatalogItem> findAvailableById(UUID itemId);

    /** 上架条目计数（按筛选条件）。query 已归一化并转义。 */
    long countOnShelf(String query, UUID categoryId, String type, boolean availabilityOnly);

    /** 分页查询上架条目。query 已归一化并转义。 */
    List<CatalogItem> searchOnShelf(String query, UUID categoryId, String type,
                                    boolean availabilityOnly, String sort,
                                    int limit, long offset);
}
