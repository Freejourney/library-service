package com.elibrary.account.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface AccountMapper {

    @Select("""
            SELECT account_id,status,eligibility_level,display_name
            FROM accounts WHERE account_id=#{id}
            """)
    AccountRow findById(@Param("id") UUID id);

    @Select("""
            SELECT account_id,status,eligibility_level,display_name
            FROM accounts WHERE account_id=#{id} FOR UPDATE
            """)
    AccountRow lockById(@Param("id") UUID id);
}