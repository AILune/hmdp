package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> cacheQuery() {
        //1、先从Redis中查询商铺类型
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range("cache:shop:type", 0, -1);
        //2、如果存在，直接返回
        if(shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            List<ShopType> shopTypeList = shopTypeJsonList.stream()
                    .map(jsonStr -> JSONUtil.toBean(jsonStr, ShopType.class))
                    .collect(Collectors.toList());
            return shopTypeList;
        }
        //3、如果不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //4、如果数据库不存在，返回错误信息
        if(shopTypeList == null || shopTypeList.isEmpty()) {
            return null;
        }
        //5、如果存在，先写入Redis缓存
        stringRedisTemplate.opsForList().rightPushAll("cache:shop:type", shopTypeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList()));
        //6、返回数据
        return shopTypeList;

    }
}
