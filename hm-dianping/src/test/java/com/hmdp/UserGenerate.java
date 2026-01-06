package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: UserGenerate
 * Package: com.hmdp
 * Description:
 *
 * @Author Jason Yee
 * @Create 2025/12/29 17:04
 * @Version 1.0
 */
@SpringBootTest
public class UserGenerate {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void bulkLogin() {
        //查询所有用户
        List<User> users = userService.list();
        //获取resource目录下的文件路径（用于存放生成的token）
        String path = new File("").getAbsolutePath() + "\\src\\main\\resources\\tokens.txt";
        //String file = Objects.requireNonNull(BulkLogin.class.getClassLoader().getResource("tokens.txt")).getFile();
        //创建字符写入流
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //创建字符写入缓冲区
        BufferedWriter bw = new BufferedWriter(fw);

        users.forEach(user -> {
            //8.保存用户信息到redis中
            //8.1随机生成token作为登录令牌
            String token = UUID.randomUUID().toString(true);
            //8.2将user对象转为hashMap存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((field, fieldValue) -> fieldValue.toString())
            );
            //System.out.println(map);
            //导入redis
            stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, map);
            //设置有效期，30分钟
            stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES);
            //这一行代码是同时获取Map中的key和value，当然和这个批量登陆功能没关系
            //Set<Map.Entry<String, Object>> set = map.entrySet();
            try {
                //开始写入
                bw.write(token + "\n");
                //强制刷新
                bw.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("运行成功！");
    }
}
