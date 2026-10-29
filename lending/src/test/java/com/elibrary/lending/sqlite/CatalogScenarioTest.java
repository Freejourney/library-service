package com.elibrary.lending.sqlite;

import com.elibrary.catalog.application.CatalogApplicationService;
import com.elibrary.catalog.application.SearchCriteria;
import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 场景 2：目录浏览/检索/详情（对应 curl-scenarios.md 第 2 章）。 */
class CatalogScenarioTest extends SqliteScenarioTestBase {

    @Autowired
    private CatalogApplicationService catalog;

    @Test
    void categoriesOnlyActiveWithOnShelfItems() {
        var categories = catalog.categories();
        assertEquals(2, categories.size());
        assertTrue(categories.stream().allMatch(c -> c.status().equals("ACTIVE")));
        assertTrue(categories.stream().anyMatch(c -> c.categoryId().equals(C1)));
        assertTrue(categories.stream().anyMatch(c -> c.categoryId().equals(C2)));
        // C3 DISABLED 不出现
        assertTrue(categories.stream().noneMatch(c -> c.categoryId().equals(C3)));
    }

    @Test
    void searchByKeyword() {
        var page = catalog.search(new SearchCriteria("quantum", null, null, null, 0, 0, null));
        assertEquals(1, page.total());
        assertEquals("Quantum Physics", page.items().get(0).title());
    }

    @Test
    void searchIsCaseInsensitive() {
        var page = catalog.search(new SearchCriteria("QUANTUM", null, null, null, 0, 0, null));
        assertEquals(1, page.total());
    }

    @Test
    void searchEscapesPercent() {
        // '%' 被转义，不应匹配任何条目
        var page = catalog.search(new SearchCriteria("%", null, null, null, 0, 0, null));
        assertEquals(0, page.total());
    }

    @Test
    void searchBlankQueryReturnsAllOnShelf() {
        var page = catalog.search(new SearchCriteria("   ", null, null, null, 0, 0, null));
        // ON_SHELF: B1 B2 B5 B6 B7 B8 B9 B10 = 8
        assertEquals(8, page.total());
    }

    @Test
    void searchQueryTooLongRejected() {
        assertThrows(ApiException.class,
                () -> catalog.search(new SearchCriteria("a".repeat(101), null, null, null, 0, 0, null)));
    }

    @Test
    void searchByCategory() {
        var page = catalog.search(new SearchCriteria(null, C2, null, null, 0, 0, null));
        assertEquals(5, page.total()); // B5 B7 B8 B9 B10
    }

    @Test
    void searchByType() {
        var page = catalog.search(new SearchCriteria(null, null, "PERIODICAL", null, 0, 0, null));
        // ON_SHELF 的 PERIODICAL 无（B3 下架）
        assertEquals(0, page.total());
    }

    @Test
    void searchAvailabilityOnly() {
        var page = catalog.search(new SearchCriteria(null, null, null, true, 0, 0, null));
        // 可借: B1(2) B5(1) B6(1) = 3
        assertEquals(3, page.total());
    }

    @Test
    void searchSortByTitle() {
        var asc = catalog.search(new SearchCriteria(null, null, null, null, 0, 0, "title_asc"));
        var titles = asc.items().stream().map(CatalogItem::title).toList();
        assertEquals(titles.stream().sorted().toList(), titles);
    }

    @Test
    void searchPageSizeTooLargeRejected() {
        assertThrows(ApiException.class,
                () -> catalog.search(new SearchCriteria(null, null, null, null, 0, 101, null)));
    }

    @Test
    void searchPageOutOfRangeConverges() {
        var page = catalog.search(new SearchCriteria(null, null, null, null, 999, 20, null));
        assertTrue(page.page() <= 1);
        assertFalse(page.items().isEmpty());
    }

    @Test
    void bookDetailOnShelf() {
        CatalogItem item = catalog.book(B1);
        assertEquals("The Great Book", item.title());
        assertEquals(2, item.availableCopies()); // U3 占用 1 本
        assertTrue(item.borrowable());
    }

    @Test
    void bookDetailNoStockStillVisible() {
        CatalogItem item = catalog.book(B2);
        assertEquals(0, item.availableCopies());
        assertFalse(item.borrowable());
    }

    @Test
    void bookDetailOffShelfNotFound() {
        assertThrows(ApiException.class, () -> catalog.book(B3));
    }

    @Test
    void bookDetailRemovedNotFound() {
        assertThrows(ApiException.class, () -> catalog.book(B4));
    }

    @Test
    void bookDetailUnknownNotFound() {
        assertThrows(ApiException.class, () -> catalog.book(UUID_FF));
    }

    @Test
    void bookDetailInvalidIdRejected() {
        assertThrows(ApiException.class, () -> catalog.book(java.util.UUID.randomUUID()));
    }

    private static final java.util.UUID UUID_FF =
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff");
}