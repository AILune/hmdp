package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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
public class LoginInterceptor implements HandlerInterceptor{
    //使用Redis实现
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从ThreadLocal获取用户
        UserDTO user =  UserHolder.getUser();

        if(user == null){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;   //不放行
        }

        //放行
        return true;
    }
}
