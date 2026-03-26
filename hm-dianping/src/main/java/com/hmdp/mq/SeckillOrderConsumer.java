package com.hmdp.mq;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * ClassName: SeckillOrderConsumer
 * Package: com.hmdp.mq
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/8 20:32
 * @Version 1.0
 */
@Component
@Slf4j
public class SeckillOrderConsumer {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillOrder(SeckillOrderMessage message, Channel channel, Message mqMessage) throws IOException {
        log.info("【消费者】收到秒杀消息：orderId={}, thread={}", message.getOrderId(), Thread.currentThread().getName());
        //业务幂等性判断   由于订单id通过全局唯一id生成，且为订单表的主键，因此可以在创建订单前查询是否存在该订单id，如果存在就不创建
        //防止同一条消息重复消费导致的库存多扣减以及订单多创建问题
        //双重幂等方案：此处业务层在创建前查询订单是否存在，同时结合数据库层设计订单id为订单表主键(order_id)，保证唯一，即使业务层漏判，数据库也会报DuplicateKey异常
        Long orderId = message.getOrderId();
        //查询订单是否存在
        VoucherOrder order = voucherOrderService.getById(orderId);
        if(order!=null){
            //订单存在，直接返回
            return;
        }
        //订单不存在，创建订单
        VoucherOrder newOrder = new VoucherOrder();
        newOrder.setId(orderId);
        newOrder.setUserId(message.getUserId());
        newOrder.setVoucherId(message.getVoucherId());

        // 这里直接调用方法即可，如果抛异常则会自动重试，直至重试耗尽，将消息发送到错误信息队列
        voucherOrderService.createVoucherOrder(newOrder);
        //如果在上面扣减库存、创建订单业务完成后且在自动ACK完成前出现如下情况：ACK未成功发送（如网络中断）、抛出异常、消费者宕机、服务重启等，可能就会导致消息重新进入队列
        //消息重新进入队列后会导致重复消费，如果没有做业务幂等性处理，就会重复上述创建订单流程，导致同一订单重复插入并多次扣减库存，因此最开头做了业务幂等性判断

        // 只留下下面两行代码，模拟异常情况，来测试消息最多重试三次就会投递到错误队列
//        System.out.println("收到消息: " + message);
//        throw new RuntimeException("测试异常");
        }
    }

//    //错误队列消费者
//    @RabbitListener(queues = RabbitMQConfig.SECKILL_ERROR_QUEUE)
//    public void handleErrorQueue(SeckillOrderMessage message) {
//        System.out.println("错误队列收到消息: " + message);
//    }


