package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: SimpleRedisLock
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/28 21:10
 * @Version 1.0
 */
//@Component
/**
 * 简单的Redis分布式锁，传入锁的名称和Redis模板
 */
public class SimpleRedisLock implements ILock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    //锁的Key前缀
    private static final String KEY_PRIFIX = "lock:";
    //锁的Value前缀，线程ID拼接UUID防止多个JVM中相同的线程ID导致仍然出现误删
    private static final String LOCK_PRIFIX = UUID.randomUUID().toString(true) + "-";


    //定义unlock的Lua脚本，DefaultRedisScript为RedisScirpt的实现类
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {    //静态代码块构造
        UNLOCK_SCRIPT = new DefaultRedisScript<>();                     //初始化RedisScript对象
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); //指定脚本路径加载脚本
        UNLOCK_SCRIPT.setResultType(Long.class);                        //设置脚本返回值类型
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = LOCK_PRIFIX + Thread.currentThread().getId();
        String key = KEY_PRIFIX + name;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //第一个参数为脚本对象，第二个参数为脚本的Key参数列表，第三个参数为脚本的Value参数列表
        //ctrl+P来分析各参数的类型
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PRIFIX + name),
                LOCK_PRIFIX + Thread.currentThread().getId());
    }

    //每个锁的key都相同，如果释放锁这里查询和删除操作不满足原子性，则会出现多线程安全问题
//    @Override
//    public void unlock() {
//        String threadId = LOCK_PRIFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PRIFIX + name);
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PRIFIX + name);
//        }
//    }
}
