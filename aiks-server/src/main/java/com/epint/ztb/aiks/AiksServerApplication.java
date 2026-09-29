package com.epint.ztb.aiks;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.epint.ztb.aiks.config.AiksProperties;

/**
 * AI 智能问答（法规咨询）服务启动类
 *
 * 独立于 epointbid8 平台（Java 8）部署，通过 HTTP 为平台侧 ztb-aiks-impl 提供服务。
 */
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AiksProperties.class)
@MapperScan("com.epint.ztb.aiks.**.mapper")
public class AiksServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiksServerApplication.class, args);
    }
}
