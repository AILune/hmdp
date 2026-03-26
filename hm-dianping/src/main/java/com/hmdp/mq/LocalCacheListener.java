//package com.hmdp.mq;
//
//import com.github.benmanes.caffeine.cache.Cache;
//import com.hmdp.config.RabbitMQConfig;
//import com.hmdp.entity.Shop;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
///**
// * 本地缓存（Caffeine）失效监听器
// * 监听 RabbitMQ 广播消息，清除本地缓存保证多节点一致
// */
//@Component
//@Slf4j
//public class LocalCacheListener {
//
//    @Autowired
//    private Cache<Long, Shop> shopLocalCache;
//
//    /**
//     * 监听本地缓存失效广播
//     * queues：监听 queue.cache.local.invalidate 队列
//     */
//    @RabbitListener(queues = RabbitMQConfig.LOCAL_CACHE_INVALIDATE_QUEUE)
//    public void handleInvalidate(Long shopId) {
//        try {
//            log.info("【本地缓存】收到失效广播，清除本地缓存：shopId={}", shopId);
//            shopLocalCache.invalidate(shopId);
//            log.info("【本地缓存】已清除本地缓存：shopId={}", shopId);
//        } catch (Exception e) {
//            log.error("【本地缓存】清除缓存失败：shopId={}", shopId, e);
//        }
//    }
//}
