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
public class SimpleRedisLock implements ILock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PRIFIX = "lock:";
    private static final String LOCK_PRIFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {    //代码块构造
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
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
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PRIFIX + name),
                LOCK_PRIFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        String threadId = LOCK_PRIFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PRIFIX + name);
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PRIFIX + name);
//        }
//    }
}
