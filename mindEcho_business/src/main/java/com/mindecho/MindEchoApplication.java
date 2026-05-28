package com.mindecho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MindEcho - AI 情绪陪伴微信小程序后端主启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class MindEchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MindEchoApplication.class, args);
    }
}

