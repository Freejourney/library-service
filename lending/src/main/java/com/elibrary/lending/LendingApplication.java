package com.elibrary.lending;

import com.elibrary.account.infrastructure.AccountRepository;
import com.elibrary.catalog.application.CatalogInventoryService;
import com.elibrary.catalog.infrastructure.CatalogItemRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** lending 服务：借阅/归还/个人记录/幂等结果。借还事务需要账号行锁与库存调整，导入 account/catalog 的实现。 */
@SpringBootApplication(scanBasePackages = {"com.elibrary.lending", "com.elibrary.config"})
@MapperScan({"com.elibrary.lending.infrastructure", "com.elibrary.account.infrastructure", "com.elibrary.catalog.infrastructure"})
@Import({AccountRepository.class, CatalogItemRepository.class, CatalogInventoryService.class})
@ConfigurationPropertiesScan(basePackages = "com.elibrary")
@EnableScheduling
public class LendingApplication {
    public static void main(String[] args) {
        SpringApplication.run(LendingApplication.class, args);
    }
}
