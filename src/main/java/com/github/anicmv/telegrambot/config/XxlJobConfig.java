package com.github.anicmv.telegrambot.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description xxl-job 执行器装配配置。
 * 声明 {@link XxlJobSpringExecutor} 后，应用启动即向调度中心自动注册，
 * 业务侧只需在 Bean 方法上标注 {@code @XxlJob("handlerName")}。
 */
@Configuration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobProperties.Executor executorProps = properties.getExecutor();
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(executorProps.getAppname());
        executor.setAddress(executorProps.getAddress());
        executor.setIp(executorProps.getIp());
        executor.setPort(executorProps.getPort());
        executor.setLogPath(executorProps.getLogPath());
        executor.setLogRetentionDays(executorProps.getLogRetentionDays());
        return executor;
    }
}
