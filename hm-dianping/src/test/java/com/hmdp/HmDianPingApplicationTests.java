package com.hmdp;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.config.RedissonConfig;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Var;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private Cache<Long, Shop> shopLocalCache;


//    @Test
//    public void testSaveShop() throws InterruptedException {
//        Shop shop = shopService.getById(1L);
//        cacheClient.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shop, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
//    }

    @Test
    public void testSave() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L, TimeUnit.SECONDS);
    }

    @Test
    public void testClearCache(){
        shopLocalCache.invalidateAll();
    }
//
//    private ExecutorService es = Executors.newFixedThreadPool(500);
//
//    @Test
//    public void testRedisIdWorker() throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(300); //这里执行任务的线程数是300而不是线程池的总线程数500
//        Runnable task = () -> {
//            for (int i = 0; i < 100; i++) {
//                long id = redisIdWorker.nextId("order");
//                System.out.println("id = " + id);
//            }
//            latch.countDown();
//        };
//        long begin = System.currentTimeMillis();
//        for (int i = 0; i < 300; i++) {
//            es.submit(task);
//        }
//        latch.await();
//        long end = System.currentTimeMillis();
//        System.out.println("耗时：" + (end - begin));
//    }

    private RLock lock;
    @BeforeEach
    void setUp(){
        lock = redissonClient.getLock("lock");
    }

    @Test
    public void test1() throws InterruptedException {
        //线程1获取锁
        boolean isLock = this.lock.tryLock(1L, TimeUnit.SECONDS);
        if(!isLock){
            log.error("method1获取锁失败");
        }
        try {
            log.info("method1获取锁成功");
            test2();
        }
        finally {
            log.info("method1释放锁");
            lock.unlock();
        }
    }

    @Test
    public void test2(){
        //线程2获取锁
        boolean isLock= lock.tryLock();
        if(!isLock){
            log.error("method2获取锁失败");
        }
        try {
            log.info("method2获取锁成功");
        }
        finally {
            log.info("method2释放锁");
            lock.unlock();
        }
    }

    //将店铺数据（店铺ID，经纬度）加载到Redis中
    @Test
    public void LoadShopData(){
        //查询所有店铺
        List<Shop> shop = shopService.list();
        //使用Map将类型不同的店铺分组
        Map<Long, List<Shop>> map = shop.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //将相同类型的店铺存入Redis
        for(Map.Entry<Long, List<Shop>> entry : map.entrySet()){
            //获取店铺类型
            Long type = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + type;
            //获取同类型的所有Shop
            List<Shop> shops = entry.getValue();
            //将shopId，经纬度加入Geo中
            for (Shop s : shops) {
                stringRedisTemplate.opsForGeo().add(key, new Point(s.getX(), s.getY()), s.getId().toString());
            }
        }
    }

    @Test
    public void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j==999){
                stringRedisTemplate.opsForHyperLogLog().add("hl1", values);
            }
        }
        System.out.println(stringRedisTemplate.opsForHyperLogLog().size("hl1"));
    }
}
