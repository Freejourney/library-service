package com.elibrary.config;

import com.elibrary.common.ApiErrorHandler;
import com.elibrary.common.LendingPolicyPort;
import com.elibrary.common.TraceFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** 共享基础设施自动配置：安全、时钟、借阅策略默认实现（各服务自动加载）。 */
@AutoConfiguration
@Import({SecurityConfig.class, TimeConfig.class, TraceFilter.class, ApiErrorHandler.class})
@EnableConfigurationProperties(LoanProperties.class)
public class ElibraryCommonAutoConfiguration {

    /** LendingPolicyPort 默认实现（读取 elibrary.loan.* 配置）；lending 服务提供 @Primary 实现覆盖。 */
    @Bean
    @ConditionalOnMissingBean(LendingPolicyPort.class)
    LendingPolicyPort lendingPolicyPort(LoanProperties properties) {
        return new ConfiguredLendingPolicy(properties);
    }
}
