package com.elibrary.account.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.UUID;

/** 账号资格所需的最小借阅计数（视图性读取 loans 表；写入仍由 lending 模块负责）。 */
@Mapper
public interface LoanCountMapper {

    @Select("SELECT COUNT(*) FROM loans WHERE account_id=#{account} AND returned_at IS NULL")
    long countActive(@Param("account") UUID account);

    @Select("SELECT COUNT(*) FROM loans WHERE account_id=#{account} AND returned_at IS NULL AND due_at <= #{now}")
    long countOverdue(@Param("account") UUID account, @Param("now") LocalDateTime now);
}
