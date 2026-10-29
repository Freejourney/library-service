package com.elibrary.lending.sqlite;

import com.elibrary.account.infrastructure.AccountMapper;
import com.elibrary.catalog.infrastructure.CatalogMapper;
import com.elibrary.lending.infrastructure.IdempotencyMapper;
import com.elibrary.lending.infrastructure.LoanMapper;
import com.elibrary.account.application.AccountApplicationService;
import com.elibrary.catalog.application.CatalogApplicationService;
import com.elibrary.lending.application.BorrowLoan;
import com.elibrary.lending.application.IdempotencyQueryService;
import com.elibrary.lending.application.LoanQueryService;
import com.elibrary.lending.application.ReturnLoan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 测试配置：内存数据库 + SQLite 版 Mapper（实现主接口，主 Repository 自动注入）。 */
@TestConfiguration
@Import({AccountApplicationService.class, CatalogApplicationService.class,
        BorrowLoan.class, ReturnLoan.class, LoanQueryService.class, IdempotencyQueryService.class})
public class SqliteTestConfig {

    /** 文件型 SQLite 数据源（所有连接共享同一文件，保证同库可见）。 */
    @Bean
    @Primary
    DataSource sqliteDataSource() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC"); // 触发驱动注册
        String url = "jdbc:sqlite:" + System.getProperty("java.io.tmpdir") + "/elibrary-sqlite-test.db";
        try (Connection c = java.sql.DriverManager.getConnection(url)) {
            c.setAutoCommit(true);
            for (String statement : loadSchema().split(";")) {
                String sql = statement.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                try (Statement st = c.createStatement()) {
                    st.execute(sql);
                }
            }
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl(url);
        return ds;
    }

    @Bean
    JdbcTemplate sqliteJdbcTemplate(DataSource sqliteDataSource) {
        return new JdbcTemplate(sqliteDataSource);
    }

    @Bean
    @Primary
    AccountMapper sqliteAccountMapper(JdbcTemplate sqliteJdbcTemplate) {
        return new SqliteAccountMapper(sqliteJdbcTemplate);
    }

    @Bean
    @Primary
    CatalogMapper sqliteCatalogMapper(JdbcTemplate sqliteJdbcTemplate) {
        return new SqliteCatalogMapper(sqliteJdbcTemplate);
    }

    @Bean
    @Primary
    LoanMapper sqliteLoanMapper(JdbcTemplate sqliteJdbcTemplate) {
        return new SqliteLoanMapper(sqliteJdbcTemplate);
    }

    @Bean
    @Primary
    IdempotencyMapper sqliteIdempotencyMapper(JdbcTemplate sqliteJdbcTemplate) {
        return new SqliteIdempotencyMapper(sqliteJdbcTemplate);
    }

    private static String loadSchema() {
        try (var in = SqliteTestConfig.class.getResourceAsStream("/sqlite/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("sqlite/schema.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}