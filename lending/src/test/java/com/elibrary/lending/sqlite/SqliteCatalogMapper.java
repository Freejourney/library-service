package com.elibrary.lending.sqlite;

import com.elibrary.catalog.infrastructure.CatalogMapper;
import com.elibrary.catalog.infrastructure.CategoryRow;
import com.elibrary.catalog.infrastructure.ItemRow;
import com.elibrary.common.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** SQLite 版 CatalogMapper：实现主接口，用 JdbcTemplate 执行 SQLite 语法。 */
public class SqliteCatalogMapper implements CatalogMapper {

    private final JdbcTemplate jdbc;

    public SqliteCatalogMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<CategoryRow> CATEGORY_ROW = (rs, i) -> new CategoryRow(
            Ids.fromBytes(rs.getBytes("category_id")),
            rs.getString("name"),
            rs.getString("status"),
            rs.getInt("sort_order"));

    private static final RowMapper<ItemRow> ITEM_ROW = (rs, i) -> new ItemRow(
            Ids.fromBytes(rs.getBytes("item_id")),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("creator"),
            Ids.fromBytes(rs.getBytes("category_id")),
            rs.getString("category_name"),
            rs.getString("description"),
            rs.getString("publisher"),
            toLocalDate(rs, "publication_date"),
            rs.getString("language_code"),
            rs.getString("identifier_type"),
            rs.getString("identifier_value"),
            rs.getString("cover_url"),
            rs.getString("shelf_status"),
            rs.getInt("total_copies"),
            rs.getInt("available_copies"),
            toLocalDateTime(rs, "published_at"));

    private static LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v == null ? null : LocalDate.parse(v);
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        if (v == null) {
            return null;
        }
        // SQLite CURRENT_TIMESTAMP 输出 'YYYY-MM-DD HH:MM:SS'，LocalDateTime.toString() 输出 'YYYY-MM-DDTHH:MM:SS'
        return LocalDateTime.parse(v.replace(' ', 'T'));
    }

    @Override
    public List<CategoryRow> listActiveCategories() {
        return jdbc.query("""
                SELECT c.category_id,c.name,c.status,c.sort_order
                FROM categories c
                WHERE c.status='ACTIVE' AND EXISTS (
                  SELECT 1 FROM catalog_items i
                  WHERE i.category_id=c.category_id AND i.shelf_status='ON_SHELF'
                )
                ORDER BY c.sort_order,c.name
                """, CATEGORY_ROW);
    }

    @Override
    public ItemRow findAvailableById(UUID id) {
        return jdbc.query("""
                SELECT i.item_id,i.type,i.title,i.creator,i.category_id,c.name category_name,
                       i.description,i.publisher,i.publication_date,i.language_code,
                       i.identifier_type,i.identifier_value,i.cover_url,i.shelf_status,
                       i.total_copies,i.available_copies,i.published_at
                FROM catalog_items i
                JOIN categories c ON c.category_id=i.category_id
                WHERE i.item_id=? AND i.shelf_status='ON_SHELF'
                """, ITEM_ROW, Ids.bytes(id)).stream().findFirst().orElse(null);
    }

    @Override
    public ItemRow lockById(UUID id) {
        return jdbc.query("""
                SELECT item_id,type,title,creator,category_id,NULL category_name,description,publisher,
                       publication_date,language_code,identifier_type,identifier_value,cover_url,
                       shelf_status,total_copies,available_copies,published_at
                FROM catalog_items WHERE item_id=?
                """, ITEM_ROW, Ids.bytes(id)).stream().findFirst().orElse(null);
    }

    @Override
    public long countOnShelf(String q, UUID categoryId, String type, boolean availabilityOnly) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM catalog_items i WHERE i.shelf_status='ON_SHELF'");
        List<Object> args = new java.util.ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND i.category_id=?");
            args.add(Ids.bytes(categoryId));
        }
        if (type != null) {
            sql.append(" AND i.type=?");
            args.add(type);
        }
        if (availabilityOnly) {
            sql.append(" AND i.available_copies > 0");
        }
        if (q != null && !q.isEmpty()) {
            sql.append(" AND i.search_text LIKE ? ESCAPE '\\'");
            args.add("%" + q + "%");
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<ItemRow> searchOnShelf(String q, UUID categoryId, String type,
                                       boolean availabilityOnly, String sort,
                                       int limit, long offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.item_id,i.type,i.title,i.creator,i.category_id,c.name category_name,
                       i.description,i.publisher,i.publication_date,i.language_code,
                       i.identifier_type,i.identifier_value,i.cover_url,i.shelf_status,
                       i.total_copies,i.available_copies,i.published_at
                FROM catalog_items i
                JOIN categories c ON c.category_id=i.category_id
                WHERE i.shelf_status='ON_SHELF'
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND i.category_id=?");
            args.add(Ids.bytes(categoryId));
        }
        if (type != null) {
            sql.append(" AND i.type=?");
            args.add(type);
        }
        if (availabilityOnly) {
            sql.append(" AND i.available_copies > 0");
        }
        if (q != null && !q.isEmpty()) {
            sql.append(" AND i.search_text LIKE ? ESCAPE '\\'");
            args.add("%" + q + "%");
        }
        sql.append("""
                 ORDER BY
                 CASE WHEN ? = 'title_asc' THEN i.title END ASC,
                 CASE WHEN ? = 'title_desc' THEN i.title END DESC,
                 i.published_at DESC,i.title ASC,
                 i.item_id ASC
                 LIMIT ? OFFSET ?
                """);
        args.add(sort);
        args.add(sort);
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ITEM_ROW, args.toArray());
    }

    @Override
    public int decrementCopies(UUID id) {
        return jdbc.update("""
                UPDATE catalog_items
                SET available_copies=available_copies-1,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE item_id=? AND available_copies>0 AND shelf_status='ON_SHELF'
                """, Ids.bytes(id));
    }

    @Override
    public int incrementCopies(UUID id) {
        return jdbc.update("""
                UPDATE catalog_items
                SET available_copies=available_copies+1,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE item_id=? AND available_copies<total_copies
                """, Ids.bytes(id));
    }
}