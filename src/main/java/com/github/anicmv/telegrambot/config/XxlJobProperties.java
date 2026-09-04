package com.github.anicmv.telegrambot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description xxl-job 执行器配置属性，映射 xxl.job 前缀。
 * 全项目定时/批量任务统一通过 xxl-job 调度，本配置为执行器侧通用接入配置。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /**
     * 与调度中心一致的通信令牌。
     */
    private String accessToken;

    private Admin admin = new Admin();

    private Executor executor = new Executor();

    @Setter
    @Getter
    public static class Admin {
        /**
         * 调度中心地址，多个逗号分隔，如 "http://host1:8081/xxl-job-admin,http://host2:8081/xxl-job-admin"。
         */
        private String addresses;
    }

    @Setter
    @Getter
    public static class Executor {
        /**
         * 执行器 AppName，需与调度中心注册时一致。
         */
        private String appname;

        /**
         * 执行器通信地址，留空表示自动注册。
         */
        private String address;

        /**
         * 执行器 IP，留空表示自动获取。
         */
        private String ip;

        /**
         * 执行器内嵌服务端口。
         */
        private int port = 9999;

        /**
         * 任务执行日志存储路径。
         */
        private String logPath = "./logs/xxl-job/jobhandler";

        /**
         * 任务执行日志保留天数，超过则自动清理；-1 表示永久保留。
         */
        private int logRetentionDays = 30;
    }
}
