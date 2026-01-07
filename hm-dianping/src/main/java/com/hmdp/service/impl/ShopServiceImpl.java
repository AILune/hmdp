package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private Cache<Long, Shop> shopLocalCache;
    //使用Redis添加商铺缓存
//    @Override
//    public Result queryById(Long id) {
//        //根据id查询店铺时，添加主动更新和超时剔除策略
//        //1、根据id在Redis中查询数据
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2、如果存在，直接返回
//        if(StrUtil.isNotBlank(shopJson)) {
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        //3、如果不存在，根据id查询数据库
//        Shop shop = getById(id);
//        //4、如果数据库中不存在，返回错误
//        if(shop == null){
//            return Result.fail("店铺不存在");
//        }
//        //5、如果数据库中存在，将数据写入Redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //6、返回数据
//        return Result.ok(shop);
//    }
    @Override
    public Result queryById(Long id) {
//        //解决缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        //自定义工具类解决缓存穿透（使用缓存空对象方式）
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        if(shop == null){
//            return Result.fail("店铺不存在");
//        }
//
//        //解决缓存击穿（互斥锁）
//        Shop shop = queryWithMutex(id);
//        if(shop == null){
//            return Result.fail("店铺不存在");
//        }
//
//        解决缓存击穿（逻辑过期）
        Shop shop = queryWithLogicExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
//        自定义工具类解决缓存击穿（使用逻辑过期方式）
//        Shop shop = cacheClient.queryWithLogicExpire(
//                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回数据
        return Result.ok(shop);
    }
    //获取互斥锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放互斥锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //使用缓存空对象方式解决缓存穿透
    public Shop queryWithPassThrough(Long id){
        //1、根据id在Redis中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、如果命中且不为空字符串""，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3、否则判断是否为空字符串""，如果是则表示查询到了空字符串，但是此时表示缓存和数据库中都不存在待查询的信息，因此返回错误信息
        if(shopJson != null) {      //这里因为已经通过isNotBlank判断过shopJson目前为""、null或者'\t\n'，因此只要不为null就表示为空字符串
            return null;
        }
        //3、如果也不为空字符串，代表缓存中无数据，未命中，此时需要查询数据库
        Shop shop = getById(id);    //调用MyBatisPlus中的getById方法
        //4、如果数据库不存在，此时说明查询的数据在缓存和数据库中均不存在，发生了缓存穿透，这里采用缓存空对象解决
        if(shop == null) {
            //缓存空对象
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5、如果存在，写入Redis缓存并设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6、返回数据
        return shop;
    }

    //使用互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id){
        //1、根据id在Redis中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、如果命中且不为空字符串，表示存在，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3、否则判断是否为空字符串""，如果是则表示查询到了空字符串，但是此时表示缓存和数据库中都不存在待查询的信息，因此返回错误信息
        if(shopJson != null) {      //这里因为已经通过isNotBlank判断过shopJson目前为""、null或者'\t\n'，因此只要不为null就表示为空字符串
            return null;
        }
        //4、如果都不存在，表示未命中缓存，此时需要查询数据库。先获取互斥锁（这里通过Redis中的setnx方法实现互斥锁，因此互斥锁的key不是缓存数据的key）
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;    //调用MyBatisPlus中的getById方法
        try {
            boolean isLock = tryLock(lockKey);
            if(!isLock){
                //5、如果获取互斥锁失败，则休眠一段时间，然后重复查询缓存操作
                Thread.sleep((50));
                return queryWithMutex(id);
            }

            //6、如果获取成功，则执行查询数据库操作，并写入缓存，返回数据
            shop = getById(id);
            Thread.sleep(200);  //模拟该热点Key重建复杂的场景
            //7、如果数据库不存在，此时说明查询的数据在缓存和数据库中均不存在，发生了缓存穿透，这里先采用缓存空对象解决
            if(shop == null) {
                //缓存空对象
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //8、如果存在，写入Redis缓存并设置过期时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //9、释放互斥锁
            unlock(lockKey);
        }
        //10、返回数据
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    //使用逻辑过期方式解决缓存击穿（这里不用考虑缓存穿透了，因为Key不会过期，不会发现查询的数据在Redis中找不到的情况）
//    //以下代码通过添加日志来验证多种情况下的多级缓存是否生效
//    public Shop queryWithLogicExpire(Long id){
//        log.info("【请求进入】queryWithLogicExpire id={}, thread={}",
//                id, Thread.currentThread().getName());
//        //先查本地缓存Caffeine
//        Shop localShop = shopLocalCache.getIfPresent(id);   //这里如果后续不止商户缓存的话，id要换成key
//        if(localShop != null){
//            log.info("【本地缓存命中】id={}, thread={}",
//                    id, Thread.currentThread().getName());
//            return localShop;
//        }
//        //如果本地缓存不存在，则：
//        //1、根据id在Redis中查询数据
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2、如果不存在，直接返回null
//        if(StrUtil.isBlank(shopJson)) {
//            log.warn("【Redis 未命中】id={}, thread={}",
//                    id, Thread.currentThread().getName());
//            return null;
//        }
//        log.info("【Redis 命中】id={}, thread={}",
//                id, Thread.currentThread().getName());
//        //3、如果存在，先做对象类型的转换
//        //3.1获取redisData对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        //3.2获取Shop对象
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //4、获取过期时间，并判断逻辑缓存是否过期，如果未过期，直接返回数据，并将数据写入本地缓存
//        if(redisData.getExpireTime().isAfter(LocalDateTime.now())) {
//            log.info("【逻辑未过期，写入本地缓存】id={}, expireTime={}, now={}, thread={}",
//                    id,
//                    redisData.getExpireTime(),
//                    LocalDateTime.now(),
//                    Thread.currentThread().getName());
//            shopLocalCache.put(id, shop);
//            return shop;
//        }
//        //5、如果过期，则需要进行缓存的重建。先获取互斥锁，如果获取成功，则新开启一个线程完成缓存的重构，同时返回过期数据
//        log.warn("【逻辑已过期】id={}, expireTime={}, now={}, thread={}",
//                id,
//                redisData.getExpireTime(),
//                LocalDateTime.now(),
//                Thread.currentThread().getName());
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        if(isLock){
//            log.info("【获取锁成功，提交重建任务】id={}, lockKey={}, thread={}",
//                    id, lockKey, Thread.currentThread().getName());
//            //获取线程池
//            CACHE_REBUILD_EXECUTOR.submit(() ->{
//                try {
//                    log.info("【开始缓存重建】id={}, rebuildThread={}",
//                            id, Thread.currentThread().getName());
//                    //重建数据
//                    this.saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //7、释放互斥锁
//                    unlock(lockKey);
//                    shopLocalCache.invalidate(id);  //在重建完毕释放互斥锁时将本地缓存清除，主动删除旧数据，防止返回旧数据时写入本地缓存后，在本地缓存过期之前无法读取重建的新数据
//                    log.info("【缓存重建完成】id={}, 已释放锁并清理本地缓存, rebuildThread={}",
//                            id, Thread.currentThread().getName());
//                }
//            });
//        }
//        log.info("【获取锁失败，返回旧数据并写入本地缓存】id={}, thread={}",
//                id, Thread.currentThread().getName());
//        //6、如果获取互斥锁失败，直接返回过期数据，获取互斥锁成功返回的过期数据也可以在这里进行
//        //同时需要写入本地缓存，这一步会导致本地缓存过期之前无法读取重建的新数据，因此可以在重建完毕释放互斥锁时将本地缓存清除，主动删除旧数据
//        shopLocalCache.put(id, shop);
//        return shop;
//    }

    /**
     * 使用【逻辑过期】方式解决缓存击穿问题
     *
     * 核心思路：
     * 1. 先查本地缓存（Caffeine），命中直接返回
     * 2. 本地缓存未命中，查询 Redis
     * 3. Redis 不存在数据，直接返回 null
     * 4. Redis 存在数据：
     *    - 若逻辑未过期：返回数据，并写入本地缓存
     *    - 若逻辑已过期：
     *        - 尝试获取互斥锁
     *        - 获取成功：异步重建缓存，重建完成后释放锁并删除旧的本地缓存数据
     *        - 获取失败：直接返回旧数据
     *    - 返回旧数据前写入本地缓存（重建完成后会主动清理）
     *
     * @param id 商户 id
     * @return Shop
     */
    public Shop queryWithLogicExpire(Long id) {

        /* ===================== 1. 查询本地缓存 ===================== */
        // 先从 Caffeine 本地缓存中查询
        Shop localShop = shopLocalCache.getIfPresent(id);
        if (localShop != null) {
            return localShop;
        }

        /* ===================== 2. 查询 Redis ===================== */
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // Redis 中不存在数据，直接返回
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        /* ===================== 3. 反序列化 Redis 数据 ===================== */
        // Redis 中存的是 RedisData（包含数据本身 + 逻辑过期时间）
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        /* ===================== 4. 判断逻辑是否过期 ===================== */
        // 逻辑未过期：直接返回数据，并写入本地缓存
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            shopLocalCache.put(id, shop);
            return shop;
        }

        /* ===================== 5. 逻辑已过期，尝试重建缓存 ===================== */
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 获取锁成功：开启异步线程进行缓存重建
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 从数据库查询最新数据并写入 Redis（重新设置逻辑过期时间）
                    this.saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                }catch (Exception e){
                    throw new RuntimeException(e);
                } finally {
                    // 重建完成后释放锁
                    unlock(lockKey);
                    // 主动清除本地缓存，防止旧数据在本地缓存中继续生效
                    shopLocalCache.invalidate(id);
                }
            });
        }

        /* ===================== 6. 返回旧数据 ===================== */
        // 无论是否获取到锁，都直接返回旧数据，保证接口可用性
        // 同时将旧数据写入本地缓存（重建完成后会被主动清除）
        shopLocalCache.put(id, shop);
        return shop;
    }

    //将Shop对象写入Redis缓存，并添加逻辑过期时间
    public void saveShop2Redis(Long id, Long expireTimes, TimeUnit unit) throws InterruptedException {
        //1、根据id查询Shop对象
        Shop shop = getById(id);
        Thread.sleep(200);  //模拟该热点Key重建复杂的场景
        //2、先将Shop对象转换为RedisData对象
        RedisData redisData = new RedisData();
        long expireSeconds = unit.toSeconds(expireTimes);
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3、将RedisData对象转换为JSON字符串
        String shopJson = JSONUtil.toJsonStr(redisData);
        //4、将JSON字符串写入Redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shopJson);
    }

    @Override
    @Transactional  //同时操作了数据库和Redis，需要事务管理
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1、先更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //先判断是否需要根据地理坐标查询
        if(x == null || y == null) {
            //根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //设置分页参数
        Integer from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        Integer end = current * SystemConstants.DEFAULT_PAGE_SIZE;


        String key = RedisConstants.SHOP_GEO_KEY + typeId;

        //查询Redis指定范围内的店铺信息     //GEOSEARCH g1 fromlonlat x y byradius 10 km withdist
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        if(results == null) {
            return Result.ok(Collections.emptyList());
        }

        //获得店铺的信息
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(from >= content.size()) {
            return Result.ok(Collections.emptyList());
        }
        //解析店铺信息
        List<Long> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        content.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //根据店铺id查询店铺信息
        List<Shop> shops = query().in("id", ids).last("order by field(id," + StrUtil.join(",", ids) + ")").list();
        //将距离信息添加到店铺对象中
        for(Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回数据
        return Result.ok(shops);
    }
}
