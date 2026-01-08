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
    public void handleSeckillOrder(
            SeckillOrderMessage message,
            Channel channel,
            Message mqMessage
    ) throws IOException {
        try {
//            if (true) { throw new RuntimeException("测试异常"); } //新增异常，测试发现存在异常时会重复消费，Message不会消失
            VoucherOrder order = new VoucherOrder();
            order.setId(message.getOrderId());
            order.setUserId(message.getUserId());
            order.setVoucherId(message.getVoucherId());

            voucherOrderService.createVoucherOrder(order);

            // 手动 ACK
            channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("处理秒杀订单异常", e);
            // 重新入队（类似 pending-list）
            channel.basicNack(
                    mqMessage.getMessageProperties().getDeliveryTag(),
                    false,
                    true
            );
        }
    }
}

