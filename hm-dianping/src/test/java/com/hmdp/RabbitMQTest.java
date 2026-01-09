package com.hmdp;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

/**
 * ClassName: RabbitMQTest
 * Package: com.hmdp
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/9 10:34
 * @Version 1.0
 */
@SpringBootTest
public class RabbitMQTest {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Test
    public void testPageOut(){
        Message message = MessageBuilder
                .withBody("hello".getBytes(StandardCharsets.UTF_8))
                        .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT).build();
        for (int i = 0; i < 1000000; i++) {
            rabbitTemplate.convertAndSend("seckill.order.queue", message);
        }
    }

    @Test
    public void testError(){
        SeckillOrderMessage message = new SeckillOrderMessage(1L, 123L, 456L);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
    }
}
