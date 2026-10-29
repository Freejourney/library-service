package com.elibrary.catalog.api;

import com.elibrary.catalog.application.CatalogApplicationService;
import com.elibrary.common.LendingPolicyPort;
import com.elibrary.catalog.application.SearchCriteria;
import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.common.ApiResponses;
import com.elibrary.common.Ids;
import com.elibrary.common.Maps;
import com.elibrary.common.Paging;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 目录检索与详情（匿名可访问）。 */
@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private final CatalogApplicationService catalog;
    private final LendingPolicyPort lendingPolicy;

    public BookController(CatalogApplicationService catalog, LendingPolicyPort lendingPolicy) {
        this.catalog = catalog;
        this.lendingPolicy = lendingPolicy;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean availability,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            HttpServletRequest request) {
        SearchCriteria criteria = new SearchCriteria(
                q, categoryId == null ? null : Ids.parse(categoryId), type, availability,
                page == null ? 0 : page, pageSize == null ? 0 : pageSize, sort);
        CatalogApplicationService.Page<CatalogItem> result = catalog.search(criteria);
        Paging<Map<String, Object>> payload = new Paging<>(
                result.items().stream().map(this::itemSummary).toList(),
                result.page(), result.pageSize(), result.total(), result.sort());
        return ApiResponses.ok(payload, request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> book(@PathVariable String id, HttpServletRequest request) {
        CatalogItem item = catalog.book(Ids.parse(id));
        return ApiResponses.ok(itemDetail(item), request);
    }

    private Map<String, Object> itemSummary(CatalogItem item) {
        return Maps.of(
                "id", item.itemId(),
                "type", item.type(),
                "title", item.title(),
                "creator", item.creator(),
                "categoryId", item.categoryId(),
                "categoryName", item.categoryName(),
                "availableCount", item.availableCopies(),
                "availability", item.availableCopies() > 0 ? "AVAILABLE" : "UNAVAILABLE",
                "publicationDate", item.publicationDate());
    }

    private Map<String, Object> itemDetail(CatalogItem item) {
        LendingPolicyPort.LoanPolicyView policy = lendingPolicy.loanPolicy();
        return Maps.of(
                "id", item.itemId(),
                "type", item.type(),
                "title", item.title(),
                "creator", item.creator(),
                "category", Maps.of("id", item.categoryId(), "name", item.categoryName()),
                "description", item.description(),
                "publisher", item.publisher(),
                "publicationDate", item.publicationDate(),
                "language", item.languageCode(),
                "identifierType", item.identifierType(),
                "identifierValue", item.identifierValue(),
                "coverUrl", item.coverUrl(),
                "availableCount", item.availableCopies(),
                "availability", item.availableCopies() > 0 ? "AVAILABLE" : "UNAVAILABLE",
                "loanPolicy", Maps.of(
                        "loanDays", policy.loanDays(),
                        "dueTime", policy.dueTime(),
                        "timeZone", policy.timeZone(),
                        "maxActiveLoans", policy.maxActiveLoans(),
                        "renewal", policy.renewal()));
    }
}
