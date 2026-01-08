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
import org.springframework.stereotype.Component;

import java.io.IOException;

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
        try {
//            if (true) { throw new RuntimeException("测试异常"); } //新增异常，测试发现：存在异常时会重复消费，Message不会消失（会重新入队）
            VoucherOrder order = new VoucherOrder();
            order.setId(message.getOrderId());
            order.setUserId(message.getUserId());
            order.setVoucherId(message.getVoucherId());

            voucherOrderService.createVoucherOrder(order);

            // 订单正常创建则手动ACK
            // channel.basicAck(deliveryTag, multiple)  deliveryTag为消息唯一标识  multiple为false表示只确认当前消息，不批量确认其他消息
            channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理秒杀订单异常", e);
            // 消息处理异常时，手动NACK，通知RabbitMQ重新入队（类似 pending-list）
            // channel.basicNack(deliveryTag, multiple, requeue)    requeue为true表示异常时将消息重新入队等待重试
            channel.basicNack(mqMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}

