package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: ThreadPoolConfig
 * Package: com.hmdp.config
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/3/18 15:21
 * @Version 1.0
 */
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor cacheRebuildExecutor(){
        return new ThreadPoolExecutor(
                4,
                10,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}

