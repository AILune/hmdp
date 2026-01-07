package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
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
        //解决缓存击穿（逻辑过期）
        Shop shop = queryWithLogicExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //自定义工具类解决缓存击穿（使用逻辑过期方式）
//        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
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

    //使用逻辑过期方式解决缓存击穿（这里不用考虑缓存穿透了，因为Key不会过期，不会发现查询的数据在Redis中找不到的情况）
    public Shop queryWithLogicExpire(Long id){
        //1、根据id在Redis中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、如果不存在，直接返回null
        if(StrUtil.isBlank(shopJson)) {
            return null;
        }
        //3、如果存在，先做对象类型的转换
        //3.1获取redisData对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //3.2获取Shop对象
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //4、获取过期时间，并判断逻辑缓存是否过期，如果未过期，直接返回数据
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        //5、如果过期，则需要进行缓存的重建。先获取互斥锁，如果获取成功，则新开启一个线程完成缓存的重构，同时返回过期数据
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //获取线程池
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建数据
                    this.saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //7、释放互斥锁
                    unlock(lockKey);
                }
            });
        }
        //6、如果获取失败，直接返回过期数据，上一步的返回过期数据也可以在这里进行
        return shop;
    }

    //将Shop对象写入Redis缓存，并添加逻辑过期时间
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1、根据id查询Shop对象
        Shop shop = getById(id);
        Thread.sleep(200);  //模拟该热点Key重建复杂的场景
        //2、先将Shop对象转换为RedisData对象
        RedisData redisData = new RedisData();
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
