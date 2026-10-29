package com.elibrary.catalog.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CatalogMapper {

    @Select("""
            SELECT c.category_id,c.name,c.status,c.sort_order
            FROM categories c
            WHERE c.status='ACTIVE' AND EXISTS (
              SELECT 1 FROM catalog_items i
              WHERE i.category_id=c.category_id AND i.shelf_status='ON_SHELF'
            )
            ORDER BY c.sort_order,c.name
            """)
    List<CategoryRow> listActiveCategories();

    @Select("""
            SELECT i.item_id,i.type,i.title,i.creator,i.category_id,c.name category_name,
                   i.description,i.publisher,i.publication_date,i.language_code,
                   i.identifier_type,i.identifier_value,i.cover_url,i.shelf_status,
                   i.total_copies,i.available_copies,i.published_at
            FROM catalog_items i
            JOIN categories c ON c.category_id=i.category_id
            WHERE i.item_id=#{id} AND i.shelf_status='ON_SHELF'
            """)
    ItemRow findAvailableById(@Param("id") UUID id);

    @Select("""
            SELECT item_id,type,title,creator,category_id,NULL category_name,description,publisher,
                   publication_date,language_code,identifier_type,identifier_value,cover_url,
                   shelf_status,total_copies,available_copies,published_at
            FROM catalog_items WHERE item_id=#{id} FOR UPDATE
            """)
    ItemRow lockById(@Param("id") UUID id);

    @Select("""
            <script>
            SELECT COUNT(*) FROM catalog_items i
            WHERE i.shelf_status='ON_SHELF'
            <if test="categoryId != null">AND i.category_id=#{categoryId}</if>
            <if test="type != null">AND i.type=#{type}</if>
            <if test="availabilityOnly">AND i.available_copies &gt; 0</if>
            <if test="q != null and q != ''">
              AND i.search_text LIKE CONCAT('%',#{q},'%') ESCAPE '\\\\'
            </if>
            </script>
            """)
    long countOnShelf(@Param("q") String q, @Param("categoryId") UUID categoryId,
                      @Param("type") String type, @Param("availabilityOnly") boolean availabilityOnly);

    @Select("""
            <script>
            SELECT i.item_id,i.type,i.title,i.creator,i.category_id,c.name category_name,
                   i.description,i.publisher,i.publication_date,i.language_code,
                   i.identifier_type,i.identifier_value,i.cover_url,i.shelf_status,
                   i.total_copies,i.available_copies,i.published_at
            FROM catalog_items i
            JOIN categories c ON c.category_id=i.category_id
            WHERE i.shelf_status='ON_SHELF'
            <if test="categoryId != null">AND i.category_id=#{categoryId}</if>
            <if test="type != null">AND i.type=#{type}</if>
            <if test="availabilityOnly">AND i.available_copies &gt; 0</if>
            <if test="q != null and q != ''">
              AND i.search_text LIKE CONCAT('%',#{q},'%') ESCAPE '\\\\'
            </if>
            ORDER BY
            <choose>
              <when test="sort == 'title_asc'">i.title ASC</when>
              <when test="sort == 'title_desc'">i.title DESC</when>
              <otherwise>i.published_at DESC,i.title ASC</otherwise>
            </choose>,
            i.item_id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ItemRow> searchOnShelf(@Param("q") String q, @Param("categoryId") UUID categoryId,
                                @Param("type") String type, @Param("availabilityOnly") boolean availabilityOnly,
                                @Param("sort") String sort, @Param("limit") int limit,
                                @Param("offset") long offset);

    @Update("""
            UPDATE catalog_items
            SET available_copies=available_copies-1,version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE item_id=#{id} AND available_copies>0 AND shelf_status='ON_SHELF'
            """)
    int decrementCopies(@Param("id") UUID id);

    @Update("""
            UPDATE catalog_items
            SET available_copies=available_copies+1,version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE item_id=#{id} AND available_copies<total_copies
            """)
    int incrementCopies(@Param("id") UUID id);
}