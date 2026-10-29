package com.elibrary.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** catalog 服务：目录浏览/检索/详情。 */
@SpringBootApplication(scanBasePackages = {"com.elibrary.catalog", "com.elibrary.config"})
@MapperScan("com.elibrary.catalog.infrastructure")
@ConfigurationPropertiesScan(basePackages = "com.elibrary")
@EnableScheduling
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
