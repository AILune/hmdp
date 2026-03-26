package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {    //代码块构造
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    @SentinelResource(value = "seckillVoucher", blockHandler = "seckillBlockHandler")
    public Result seckillVoucher(Long voucherId) {
        //通过Lua脚本判断是否库存充足和一人一单，并将优惠券信息写入消息队列
        Long userId = UserHolder.getUser().getId();
        //这里的SECKILL_SCRIPT脚本中没有Key，所以传入空集合
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        //判断是否秒杀成功
        int r = result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不允许重复下单");
        }
        //秒杀成功，将订单id、优惠券id、用户id等信息发送到 MQ，然后返回订单id即可
        Long voucherOrderId = redisIdWorker.nextId("order");
        SeckillOrderMessage message = new SeckillOrderMessage(voucherOrderId, userId, voucherId);
        // 用订单号作为消息唯一标识
//        CorrelationData correlationData = new CorrelationData(voucherOrderId.toString());
        // 发送消息到MQ，异步下单，非阻塞
        log.info("秒杀成功！【生产者】发送秒杀消息：orderId={}, thread={}", voucherOrderId, Thread.currentThread().getName());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
        //返回订单ID，异步下单
        return Result.ok(voucherOrderId);
    }

    public Result seckillBlockHandler(Long voucherId, BlockException ex) {
        // 可以简单记录一下，或者直接返回
         log.warn("秒杀限流：voucherId={}", voucherId);
        return Result.fail("排队人数过多，请刷新重试");
    }

    @Transactional  //涉及多表操作，需要事务管理
    public void createVoucherOrder(VoucherOrder voucherOrder){
        Long voucherId = voucherOrder.getVoucherId();
         //扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId)
                .gt("stock", 0)
                .update();
        //更新订单表
        save(voucherOrder);
        //上面的先查订单再插入操作是从消息队列取出消息后才操作的，不会出现高并发情况下两个线程同时查询订单都为空的情况
        //因为消息进入消息队列前已经通过一人一单和库存充足的逻辑了，也就是并发重复订单已经被拦在消息队列外面了，这里只可能是消息重复消费导致一条订单多次调用创建订单函数
    }
}
