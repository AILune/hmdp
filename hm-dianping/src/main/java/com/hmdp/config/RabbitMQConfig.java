package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * ClassName: RabbitMQConfig
 * Package: com.hmdp.config
 * Description:
 *
 * @Author Jason Yee
 * @Create 2026/1/8 20:18
 * @Version 1.0
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_ERROR_EXCHANGE = "seckill.error.exchange";
    public static final String SECKILL_ERROR_QUEUE = "seckill.error.queue";
    public static final String SECKILL_ERROR_ROUTING_KEY = "seckill.error";


    // 秒杀交换机
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }


    // 秒杀队列
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).lazy().build();
    }

    // 秒杀队列绑定
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder
                .bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    // 死信交换机，接收消费者重试多次后还是失败的消息
    @Bean
    public DirectExchange seckillErrorExchange() {
        return new DirectExchange(SECKILL_ERROR_EXCHANGE, true, false);
    }

    // 死信队列，接收消费者重试多次后还是失败的消息
    @Bean
    public Queue seckillErrorQueue() {
        return QueueBuilder.durable(SECKILL_ERROR_QUEUE).lazy().build();
    }

    // 死信队列绑定
    @Bean
    public Binding seckillErrorBinding() {
        return BindingBuilder
                .bind(seckillErrorQueue())
                .to(seckillErrorExchange())
                .with(SECKILL_ERROR_ROUTING_KEY);
    }

    // 重试耗尽后，将失败的消息重新发送到死信交换机
    @Bean
    public MessageRecoverer seckillMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(
                rabbitTemplate,
                RabbitMQConfig.SECKILL_ERROR_EXCHANGE,
                RabbitMQConfig.SECKILL_ERROR_ROUTING_KEY
        );
    }

    //消费者重试机制
    @Bean
    public RetryOperationsInterceptor seckillRetryInterceptor(MessageRecoverer messageRecoverer) {
        return RetryInterceptorBuilder
                .stateless()                 // 推荐：无状态重试
                .maxAttempts(3)              // 最多重试 3 次
                .recoverer(messageRecoverer) // 超过次数走 recoverer
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor seckillRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);

        //自动 ACK
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);

        //不 requeue，否则会死循环
        factory.setDefaultRequeueRejected(false);

        //注入 retry 拦截器
        factory.setAdviceChain(seckillRetryInterceptor);

        return factory;
    }
}


