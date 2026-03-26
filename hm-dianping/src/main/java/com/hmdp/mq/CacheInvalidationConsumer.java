//package com.hmdp.mq;
//
//import com.hmdp.config.RabbitMQConfig;
//import com.hmdp.dto.CanalMessage;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Component;
//
///**
// * 缓存失效消费者
// * 监听 Canal 发送的 Binlog 消息，删除 Redis 缓存并广播清除本地缓存
// */
//@Component
//@Slf4j
//public class CacheInvalidationConsumer {
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    /**
//     * 监听 Canal 发送的缓存失效消息
//     * queues：监听 queue.shop.cache.invalid 队列
//     */
//    @RabbitListener(queues = RabbitMQConfig.CANAL_SHOP_QUEUE)
//    public void handleCacheInvalidation(CanalMessage message) {
//        try {
//            log.info("【缓存消费者】收到缓存失效消息：{}", message);
//
//            Long shopId = message.getId();
//            String operationType = message.getType();
//
//            // 1. 删除 Redis 店铺详情缓存
//            String redisKey = "cache:shop:" + shopId;
//            stringRedisTemplate.delete(redisKey);
//            log.info("【缓存消费者】删除 Redis 缓存：key={}", redisKey);
//
//            // 2. 如果是 UPDATE 操作，删除 Geo 缓存（可选）
//            if ("UPDATE".equals(operationType)) {
//                // 简化处理：删除 Geo 中该店铺，下次查询自动重建
//                // 实际可根据 extraData 判断是否修改了地理位置
//                String geoKey = "shop:geo:1";  // 假设 typeId=1，实际可从消息中获取
//                stringRedisTemplate.opsForGeo().remove(geoKey, shopId.toString());
//                log.info("【缓存消费者】删除 Geo 缓存：key={}, shopId={}", geoKey, shopId);
//            }
//
//            // 3. 广播清除本地缓存（所有实例都会收到）
//            rabbitTemplate.convertAndSend(
//                    RabbitMQConfig.LOCAL_CACHE_INVALIDATE_EXCHANGE,  // 交换机
//                    "",  // Fanout 交换机不需要 routing key
//                    shopId  // 消息体
//            );
//            log.info("【缓存消费者】已发送本地缓存失效广播：shopId={}", shopId);
//
//        } catch (Exception e) {
//            log.error("【缓存消费者】缓存失效失败", e);
//            // ❌ 抛出异常，触发重试机制（3 次）+ 死信队列
//            throw e;
//        }
//    }
//}
