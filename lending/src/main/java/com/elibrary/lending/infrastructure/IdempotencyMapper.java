package com.elibrary.lending.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface IdempotencyMapper {

    @Select("""
            SELECT request_hash,http_status,response_json FROM idempotency_records
            WHERE account_id=#{account} AND operation=#{operation} AND idempotency_key=#{key}
              AND expires_at>UTC_TIMESTAMP(6)
            """)
    IdempotencyRow find(@Param("account") UUID account, @Param("operation") String operation,
                        @Param("key") String key);

    @Delete("""
            DELETE FROM idempotency_records
            WHERE account_id=#{account} AND operation=#{operation} AND idempotency_key=#{key}
              AND expires_at<=UTC_TIMESTAMP(6)
            """)
    int deleteExpired(@Param("account") UUID account, @Param("operation") String operation,
                      @Param("key") String key);

    @Insert("""
            INSERT INTO idempotency_records(account_id,operation,idempotency_key,request_hash,
                                            http_status,response_json,created_at,expires_at)
            VALUES(#{account},#{operation},#{key},#{hash},#{status},CAST(#{json} AS JSON),
                   UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL #{retention} DAY))
            """)
    int save(@Param("account") UUID account, @Param("operation") String operation,
             @Param("key") String key, @Param("hash") byte[] hash,
             @Param("status") int status, @Param("json") String json,
             @Param("retention") int retention);

    @Select("""
            SELECT request_hash,http_status,response_json FROM idempotency_records
            WHERE account_id=#{account} AND idempotency_key=#{key} AND expires_at>UTC_TIMESTAMP(6)
            ORDER BY created_at DESC LIMIT 1
            """)
    IdempotencyRow findLatest(@Param("account") UUID account, @Param("key") String key);

    @Delete("DELETE FROM idempotency_records WHERE expires_at<UTC_TIMESTAMP(6)")
    int deleteAllExpired();
}