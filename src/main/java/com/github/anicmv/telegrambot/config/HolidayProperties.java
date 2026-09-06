package com.github.anicmv.telegrambot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /holiday 使用的节假日和一言接口配置。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "bot.telegram.holiday")
public class HolidayProperties {

    private String holidayApi = "https://api.jiejiariapi.com/v1/holidays/{year}";
    private String oneYanApi = "https://jkapi.com/api/one_yan";
    private String oneYanFallback = "愿你今天也有被生活温柔以待的好运。";
    private String timezone = "Asia/Shanghai";
}
