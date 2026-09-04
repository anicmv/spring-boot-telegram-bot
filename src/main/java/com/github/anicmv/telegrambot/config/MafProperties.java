package com.github.anicmv.telegrambot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anicmv
 * @date 2026/3/18
 * @description /maf 命令配置属性。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "maf")
public class MafProperties {

    /**
     * 自定义用户等级配置
     * key: telegram userId
     * value: level (-1..100)
     */
    private Map<Long, Integer> customLevels = new HashMap<>();
}
