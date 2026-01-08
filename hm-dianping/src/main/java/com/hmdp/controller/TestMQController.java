package com.hmdp.controller;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClassName: TestMQController
 * Package: com.hmdp.controller
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/8 20:40
 * @Version 1.0
 */
@RestController
@RequestMapping("/test/mq")
public class TestMQController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/send")
    public String send() {
        SeckillOrderMessage message =
                new SeckillOrderMessage(1L, 100L, 200L);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
        return "ok";
    }
}