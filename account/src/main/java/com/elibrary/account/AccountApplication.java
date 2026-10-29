package com.elibrary.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** account 服务：账号资格查询。扫描本模块与 common 共享配置。 */
@SpringBootApplication(scanBasePackages = {"com.elibrary.account", "com.elibrary.config"})
@MapperScan("com.elibrary.account.infrastructure")
@ConfigurationPropertiesScan(basePackages = "com.elibrary")
@EnableScheduling
public class AccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
