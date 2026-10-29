package com.elibrary.catalog.application;

import java.util.UUID;

/** 库存调整端口，供 lending 模块在借还事务内调用（借出 -1 / 归还 +1）。 */
public interface CatalogInventoryPort {

    /** 借出成功则扣减可借数；条目不可借（下架/无库存）返回 false。 */
    boolean decrementCopies(UUID itemId);

    /** 归还时回补可借数；受 CHECK(available_copies <= total_copies) 保护。 */
    boolean incrementCopies(UUID itemId);
}