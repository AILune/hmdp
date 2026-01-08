package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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

    //定义代理对象（事务）
//    private IVoucherOrderService proxy;
    //订单阻塞队列（用于异步处理订单）
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024);
    //线程池
    ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//    //初始化线程任务，类一加载就执行初始化线程，然后去等待获取阻塞队列中的订单信息
//    @PostConstruct
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit((new VoucherOrderHandler()));
//    }
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    //1、获取消息队列中的订单信息  //xreadgroup group g1 c1 count 1 block 2000 streams stream.orders '>';
//                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
//                    //2、如果为空，继续尝试获取
//                    if(records == null || records.isEmpty()){
//                        continue;
//                    }
//                    //3、如果不为空，则解析队列消息并更新订单
//                    MapRecord<String, Object, Object> record = records.get(0);  //这里指定只取一条消息，因此list只有一个元素，直接get(0)获取即可
//                    //3.1 获取消息中的值
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = new VoucherOrder();
//                    BeanUtil.fillBeanWithMap(values, voucherOrder, true);
//                    proxy.createVoucherOrder(voucherOrder);
//                    //4、ACK信息
//                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                    //如果出现异常，则未ACK的消息会进入pending-list，需要在这里处理
//                    while(true){
//                        try {
//                            //1、获取队列信息  //xreadgroup group g1 c1 count 1 streams stream.orders 0;
//                            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
//                                    Consumer.from("g1", "c1"),
//                                    StreamReadOptions.empty().count(1),
//                                    StreamOffset.create("stream.orders", ReadOffset.from("0")));
//                            //2、如果为空，表示pending-list为空，则不需要再处理，跳出循环
//                            if(records == null || records.isEmpty()){
//                                break;
//                            }
//                            //3、如果不为空，则解析队列消息并更新订单
//                            MapRecord<String, Object, Object> record = records.get(0);  //这里指定只取一条消息，因此list只有一个元素，直接get(0)获取即可
//                            //获取消息中的值
//                            Map<Object, Object> value = record.getValue();
//                            VoucherOrder voucherOrder = new VoucherOrder();
//                            BeanUtil.fillBeanWithMap(value, voucherOrder, true);
//                            proxy.createVoucherOrder(voucherOrder);
//                            //4、ACK信息
//                            stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
//                        } catch (Exception e1) {
//                            log.error("处理pending-list异常", e1);  //pending-list抛异常则会结束当前循环进入下一轮循环，继续处理pending-list中的消息，因此这里不需要递归调用自己
//                            try {
//                                Thread.sleep(20);   //休眠20ms防止一直抛异常
//                            } catch (Exception ex) {
//                                ex.printStackTrace();
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    //线程任务
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while(true){
//                //获取订单信息
//                VoucherOrder voucherOrder = null;
//                try {
//                    voucherOrder = orderTasks.take();
//                    proxy.createVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("获取订单信息失败", e);
//                }
//                //创建订单
//            }
//        }
//    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {    //代码块构造
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //秒杀券下单
//    @Override
////    @Transactional  //涉及多表操作，需要事务管理
//    public Result seckillVoucher(Long voucherId) {
//        //1、根据优惠券id查询秒杀券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2、判断秒杀是否开始
//        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀未开始");
//        }
//        //3、判断秒杀是否结束
//        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束");
//        }
//        //4、判断库存是否充足
//        if(seckillVoucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//        //如果库存充足，则做一人一单的判断
//        Long userId = UserHolder.getUser().getId();
////        //使用Redisson分布式锁，创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean success = lock.tryLock();
//        if(!success){
//            return Result.fail("不允许重复下单");
//        }
//        //获取代理对象（事务）
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//        //如果库存充足，则做一人一单的判断
//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {     //为什么锁要加在这里？为什么要用intern()方法？
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
//    }

//    @Override
////    @Transactional  //涉及多表操作，需要事务管理
//    public Result seckillVoucher(Long voucherId) {
//        //通过Lua脚本判断是否库存充足和一人一单
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
//        //判断是否秒杀成功
//        int r = result.intValue();
//        if(r!=0){
//            return Result.fail(r==1?"库存不足":"不允许重复下单");
//        }
//        //封装订单信息
//        VoucherOrder voucherOrder = new VoucherOrder();
//        Long voucherOrderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(voucherOrderId);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userId);
//        //获取代理对象（事务）
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //将订单信息加入阻塞队列
//        orderTasks.add(voucherOrder);
//        //返回订单ID
//        return Result.ok(voucherOrderId);
//    }

    @Override
//    @Transactional  //涉及多表操作，需要事务管理
    public Result seckillVoucher(Long voucherId) {
        //通过Lua脚本判断是否库存充足和一人一单，并将优惠券信息写入消息队列
        Long userId = UserHolder.getUser().getId();
        //这里的SECKILL_SCRIPT脚本中没有Key，所以传入空集合
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), voucherOrderId.toString());
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        //判断是否秒杀成功
        int r = result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不允许重复下单");
        }
        // 5. 发送消息到 MQ
        Long voucherOrderId = redisIdWorker.nextId("order");
        SeckillOrderMessage message =
                new SeckillOrderMessage(voucherOrderId, userId, voucherId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
        //获取代理对象（事务）
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //将订单信息加入阻塞队列
//        orderTasks.add(voucherOrder);
        //返回订单ID，异步下单
        return Result.ok(voucherOrderId);
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
    }

//    @Transactional  //涉及多表操作，需要事务管理
//    public Result createVoucherOrder(Long voucherId){
//        Long userId = UserHolder.getUser().getId();
//        //一人一单判断
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if(count>0){
//            return Result.fail("用户已购买过该优惠券");
//        }
////        synchronized (userId.toString().intern()){      //锁加在这里有什么问题？
//            //5、扣减库存，更新秒杀券表的库存字段，并判断当前库存是否>0，通过乐观锁解决多线程安全问题
//            //一定要先扣减库存再新增订单，否则还是会出现库存正常减为0但订单数量大于初始库存数量的情况
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id",voucherId)
//                .gt("stock", 0)
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//        //6、下单成功，更新订单表
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1插入订单id
//        Long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //6.2插入用户id
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        //6.3插入秒杀券id
//        voucherOrder.setVoucherId(voucherId);
//        //6.4更新订单表
//        save(voucherOrder);
//        //7、返回订单id
//        return Result.ok(orderId);
////        }
//    }
}
