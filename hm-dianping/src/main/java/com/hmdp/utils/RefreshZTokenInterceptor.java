package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: LoginInterceptor
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/25 15:19
 * @Version 1.0
 */
@Component
public class RefreshZTokenInterceptor implements HandlerInterceptor{
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //使用Redis实现
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取当前请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;   //放行
        }
        //2、从Redis中取出用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        //3、判断用户是否存在
        if(userMap.isEmpty()){
            return true;   //放行
        }

        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //4、存在则将用户加入ThreadLocal
        UserHolder.saveUser(user);

        //5、刷新token过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //6、放行
        return true;    //放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //6、移除ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}
