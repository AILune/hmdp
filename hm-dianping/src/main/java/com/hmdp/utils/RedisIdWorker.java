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
     * 时间戳是秒级别的，与雪花算法毫秒级别不同
     * 序列号在一天内都是递增的，而与传统雪花算法每毫秒都重置不同。支持每秒更高的吞吐量，2^32约为43亿
     * 对时钟回拨问题更鲁棒，只有跨越天数的时钟回拨才可能出现重复ID问题
     * 无需管理节点ID，由Redis的increment保证原子性
     */
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();                 //获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);      //将当前时间转换为秒级时间戳，因此为秒级精度
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;            //计算时间戳（相对于2025-01-01 00:00:00的秒数）


        //生成序列号（序列号为Redis缓存中对应自增Key的值），每天一个key，因此序列号 sequence 也是每天会重置
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
