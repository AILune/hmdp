package com.hmdp.config;

import com.hmdp.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * ClassName: CaffeineCache
 * Package: com.hmdp.config
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/7 10:37
 * @Version 1.0
 */
@Configuration
public class CaffeineCache {

    @Bean
    public Cache<Long, Shop> shopLocalCache(){
        return Caffeine.newBuilder()
                .maximumSize(1000)      // 最大条目数：防止内存溢出（核心！）
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
