package com.github.anicmv;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 启动类
 */
@SpringBootApplication
@MapperScan("com.github.anicmv.telegrambot.mapper")
public class SpringBootTelegramBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootTelegramBotApplication.class, args);
    }

}
