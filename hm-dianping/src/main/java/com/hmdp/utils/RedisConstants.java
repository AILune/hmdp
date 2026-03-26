package com.hmdp.utils;

public class RedisConstants {
    //登录验证码key
    public static final String LOGIN_CODE_KEY = "login:code:";
    //登录验证码有效期
    public static final Long LOGIN_CODE_TTL = 2L;
    //登录用户Token key
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    //登录用户有效期
    public static final Long LOGIN_USER_TTL = 3600L;
    //登录用户ID key
    public static final String LOGIN_USER_KEY = "login:user:";
    //缓存空值有效期
    public static final Long CACHE_NULL_TTL = 2L;
    //缓存店铺有效期
    public static final Long CACHE_SHOP_TTL = 30L;
    //缓存店铺key
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String LOGIN_USER_TOKEN_KEY = "login:user:token:";

    public static final Long BLACK_LIST_TTL = 1L;

    public static final Long CODE_BLOCK_TTL = 1L;

    public static final String GET_CODE_LOCK = "getCodeLock:";

    public static final String GET_BLACK_LIST_LOCK_PHONE = "getBlackListLock:phone";

    public static final String GET_BLACK_LIST_LOCK_CLIENT_IPADDR = "getBlackListLock:clientIpAddr";

    // ============ 新增：本地缓存广播频道 ============
    /**
     * 本地缓存失效广播频道（Redis Pub/Sub，备用方案）
     */
    public static final String LOCAL_CACHE_INVALIDATE_CHANNEL = "cache:local:invalidate";

}
