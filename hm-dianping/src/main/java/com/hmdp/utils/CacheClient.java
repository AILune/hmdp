package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * ClassName: CacheClient
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/26 21:37
 * @Version 1.0
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T,ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID,T> dbFallback, Long time, TimeUnit unit){
        //1、根据id在Redis中查询数据
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、如果存在，直接返回
        if(StrUtil.isNotBlank(json)) {
            T t = JSONUtil.toBean(json, type);
            return t;
        }
        //如果不存在，则判断是否为空字符串""，如果是则表示查询到了空字符串，但是此时表示缓存和数据库中都不存在待查询的信息，因此返回错误信息
        if(json != null) {      //这里因为已经通过isNotBlank判断过shopJson目前为""、null或者'\t\n'，因此只要不为null就表示为空字符串
            return null;
        }
        //3、如果不存在，查询数据库
        T t = dbFallback.apply(id);
        //4、如果数据库不存在，此时说明查询的数据在缓存和数据库中均不存在，发生了缓存穿透，这里先采用缓存空对象解决
        if(t == null) {
            //缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5、如果存在，先写入Redis缓存并设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t), time, unit);
        //6、返回数据
        return t;
    }


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T, ID> T queryWithLogicExpire(String keyPrefix, ID id, Class<T> type, Function<ID,T> dbFallback, Long time, TimeUnit unit){
        //1、根据id在Redis中查询数据
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、如果不存在，直接返回null
        if(StrUtil.isBlank(json)) {
            return null;
        }
        //3、如果存在，先做对象类型的转换
        //3.1获取redisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //3.2获取Shop对象
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //4、获取过期时间，并判断逻辑缓存是否过期，如果未过期，直接返回数据
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return t;
        }
        //5、如果过期，则需要进行缓存的重建。先获取互斥锁，如果获取成功，则新开启一个线程完成缓存的重构，同时返回过期数据
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //获取线程池
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建数据
                    T t1 = dbFallback.apply(id);
                    this.setWithLogicExpire(key, t1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //7、释放互斥锁
                    unlock(lockKey);
                }
            });
        }
        //6、如果获取失败，直接返回过期数据，上一步的返回过期数据也可以在这里进行
        return t;
    }

}
