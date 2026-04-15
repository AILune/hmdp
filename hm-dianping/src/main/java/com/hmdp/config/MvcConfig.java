package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshZTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ClassName: MvcConfig
 * Package: com.hmdp.config
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/25 16:38
 * @Version 1.0
 */
@Configuration
@Slf4j
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private RefreshZTokenInterceptor refreshZTokenInterceptor;
    @Override
    //双层拦截器的注册，先执行token刷新拦截器，再执行登录拦截器，order值越小优先级越高
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
//                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/logout/**",
                        "/user/code",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**")
                .order(1);
        registry.addInterceptor(refreshZTokenInterceptor).addPathPatterns("/**").order(0);
    }
}
