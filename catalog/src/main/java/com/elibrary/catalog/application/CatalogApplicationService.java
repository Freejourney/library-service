package com.elibrary.catalog.application;

import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.catalog.domain.Category;
import com.elibrary.common.ApiException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 目录浏览 / 检索 / 详情。 */
@Service
public class CatalogApplicationService {

    public record Page<T>(List<T> items, int page, int pageSize, long total, String sort) {}

    private static final Set<String> SORTS = Set.of("publishedAt_desc", "title_asc", "title_desc");
    private static final Set<String> TYPES = Set.of("EBOOK", "PERIODICAL");
    private static final int MAX_QUERY_CODEPOINTS = 100;

    private final CatalogReadPort catalog;

    public CatalogApplicationService(CatalogReadPort catalog) {
        this.catalog = catalog;
    }

    public List<Category> categories() {
        return catalog.listActiveCategories();
    }

    public Page<CatalogItem> search(SearchCriteria criteria) {
        int page = criteria.page() == 0 ? 1 : criteria.page();
        int pageSize = criteria.pageSize() == 0 ? 20 : criteria.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid page or pageSize");
        }
        String sort = criteria.sort() == null ? "publishedAt_desc" : criteria.sort();
        if (!SORTS.contains(sort)) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid sort");
        }
        UUID categoryId = criteria.categoryId();
        if (criteria.type() != null && !TYPES.contains(criteria.type())) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid type");
        }
        String query = normalize(criteria.query());
        if (query.codePointCount(0, query.length()) > MAX_QUERY_CODEPOINTS) {
            throw new ApiException(400, "INVALID_QUERY", "Query exceeds 100 characters");
        }
        if (!query.isEmpty()) {
            query = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
        boolean availabilityOnly = Boolean.TRUE.equals(criteria.availability());
        long total = catalog.countOnShelf(query.isEmpty() ? null : query, categoryId, criteria.type(), availabilityOnly);
        int effectivePage = total == 0 ? 1 : (int) Math.min(page, Math.max(1, (total + pageSize - 1) / pageSize));
        List<CatalogItem> items = catalog.searchOnShelf(
                query.isEmpty() ? null : query, categoryId, criteria.type(),
                availabilityOnly, sort, pageSize, (long) (effectivePage - 1) * pageSize);
        return new Page<>(items, effectivePage, pageSize, total, sort);
    }

    public CatalogItem book(UUID itemId) {
        return catalog.findAvailableById(itemId)
                .orElseThrow(() -> ApiException.notFound("BOOK_NOT_FOUND"));
    }

    public Optional<CatalogItem> lockItem(UUID itemId) {
        return catalog.lockById(itemId);
    }

    /** NFKC + 小写归一化（搜索文本由应用层写入/比对）。 */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
