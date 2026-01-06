package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName: RedisIdWorker
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/27 12:25
 * @Version 1.0
 */
@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1735689600L; //2025-01-01 00:00:00

    private static final int COUNT_BITS = 32;
    /**
     * 生成全局唯一ID
     * @param keyPrefix 键前缀
     * @return 下一个ID
     */
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();                 //获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);      //将当前时间转换为秒级时间戳
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;            //计算时间戳（相对于2025-01-01 00:00:00的秒数）


        //生成序列号（序列号为Redis缓存中对应自增Key的值）
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));    //格式化当前时间为"yyyy:MM:dd"格式
        String key = "incr:" + keyPrefix + ":" + date;
        long sequence = stringRedisTemplate.opsForValue().increment(key);

        //拼接
        Long id = timeStamp << COUNT_BITS | sequence;

        //返回结果
        return id;
    }

//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
//        System.out.println(time.toEpochSecond(ZoneOffset.UTC));
//    }
}
