package com.elibrary.lending.infrastructure;

import com.elibrary.lending.domain.Loan;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LoanMapper {

    @Select("SELECT COUNT(*) FROM loans WHERE account_id=#{account} AND returned_at IS NULL")
    long countActive(@Param("account") UUID account);

    @Select("SELECT COUNT(*) FROM loans WHERE account_id=#{account} AND returned_at IS NULL AND due_at <= #{now}")
    long countOverdue(@Param("account") UUID account, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM loans WHERE account_id=#{account} AND item_id=#{item} AND returned_at IS NULL")
    long countDuplicate(@Param("account") UUID account, @Param("item") UUID item);

    @Select("""
            SELECT loan_id,account_id,item_id,status,borrowed_at,due_at,returned_at,
                   title_snapshot,creator_snapshot,type_snapshot,policy_version,NULL catalog_status
            FROM loans WHERE loan_id=#{loan} AND account_id=#{account} FOR UPDATE
            """)
    LoanRow lockById(@Param("loan") UUID loan, @Param("account") UUID account);

    @Insert("""
            INSERT INTO loans(loan_id,account_id,item_id,status,borrowed_at,due_at,
                              title_snapshot,creator_snapshot,type_snapshot,policy_version)
            VALUES(#{loanId},#{accountId},#{itemId},'ACTIVE',#{borrowedAt},#{dueAt},
                   #{titleSnapshot},#{creatorSnapshot},#{typeSnapshot},#{policyVersion})
            """)
    int insert(Loan loan);

    @Update("""
            UPDATE loans SET status=#{status},returned_at=#{returnedAt},version=version+1
            WHERE loan_id=#{loan} AND returned_at IS NULL
            """)
    int markReturned(@Param("loan") UUID loan, @Param("status") String status,
                     @Param("returnedAt") LocalDateTime returnedAt);

    @Select("""
            SELECT l.loan_id,l.account_id,l.item_id,
                   CASE WHEN l.returned_at IS NULL AND l.due_at<=#{now} THEN 'OVERDUE' ELSE l.status END status,
                   l.borrowed_at,l.due_at,l.returned_at,l.title_snapshot,l.creator_snapshot,
                   l.type_snapshot,l.policy_version,COALESCE(i.shelf_status,'REMOVED') catalog_status
            FROM loans l LEFT JOIN catalog_items i ON i.item_id=l.item_id
            WHERE l.account_id=#{account} AND l.loan_id=#{loan}
            """)
    LoanRow findOne(@Param("account") UUID account, @Param("loan") UUID loan,
                    @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT l.loan_id,l.account_id,l.item_id,
                   CASE WHEN l.returned_at IS NULL AND l.due_at&lt;=#{now} THEN 'OVERDUE' ELSE l.status END status,
                   l.borrowed_at,l.due_at,l.returned_at,l.title_snapshot,l.creator_snapshot,
                   l.type_snapshot,l.policy_version,COALESCE(i.shelf_status,'REMOVED') catalog_status
            FROM loans l LEFT JOIN catalog_items i ON i.item_id=l.item_id
            WHERE l.account_id=#{account}
            <if test="state == 'ACTIVE'">AND l.returned_at IS NULL AND l.due_at&gt;#{now}</if>
            <if test="state == 'OVERDUE'">AND l.returned_at IS NULL AND l.due_at&lt;=#{now}</if>
            <if test="state == 'COMPLETED'">AND l.returned_at IS NOT NULL</if>
            ORDER BY CASE WHEN l.returned_at IS NULL THEN 0 ELSE 1 END,
                     CASE WHEN l.returned_at IS NULL THEN l.due_at END ASC,
                     CASE WHEN l.returned_at IS NOT NULL THEN l.returned_at END DESC,
                     l.loan_id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<LoanRow> findByAccount(@Param("account") UUID account, @Param("state") String state,
                                @Param("now") LocalDateTime now, @Param("limit") int limit,
                                @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM loans WHERE account_id=#{account}
            <if test="state == 'ACTIVE'">AND returned_at IS NULL AND due_at&gt;#{now}</if>
            <if test="state == 'OVERDUE'">AND returned_at IS NULL AND due_at&lt;=#{now}</if>
            <if test="state == 'COMPLETED'">AND returned_at IS NOT NULL</if>
            </script>
            """)
    long countByAccount(@Param("account") UUID account, @Param("state") String state,
                        @Param("now") LocalDateTime now);
}