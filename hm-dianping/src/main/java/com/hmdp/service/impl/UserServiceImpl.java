package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
//import jdk.vm.ci.meta.Local;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //使用Session实现发送验证码
//    @Override
//    public Result sendCode(String phone, HttpSession session) {
//        //校验手机号，如果不符合规范，则返回错误信息
//        if(RegexUtils.isPhoneInvalid(phone)){
//            return Result.fail("手机号格式错误！");
//        }
//
//        //符合则生成验证码
//        String code = RandomUtil.randomNumbers(6);
//
//        //保存验证码到Session中
//        session.setAttribute("code", code);
//
//        //发送验证码
//        // TODO 调用短信服务发送验证码
//        log.debug("发送验证码成功：{}", code);
//
//        //返回ok
//        return Result.ok();
//    }
//
//    //使用Session实现登录校验
//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        //校验手机号，如果不符合规范，则返回错误信息
//        String phone = loginForm.getPhone();
//        if(RegexUtils.isPhoneInvalid(phone)){
//            return Result.fail("手机号格式错误！");
//        }
//
//        //校验验证码，如果验证码不正确，则返回错误信息
//        String code = loginForm.getCode();
//        //从Session中取出保存的验证码
//        String cacheCode = session.getAttribute("code").toString();
//        //校验验证码是否一致
//        if(!cacheCode.equals(code) || cacheCode == null){
//            return Result.fail("验证码错误！");
//        }
//
//        //根据手机号从数据库查找用户信息   select * from user where phone = ?
//        User user = query().eq("phone", phone).one();
//
//        //如果不存在用户信息，则执行注册，将用户信息保存到数据库，并将用户信息保存在Session中
//        if(user == null){
//            //注册用户
//            user = createUserWithPhone(phone);
//        }
//
//        //如果存在用户信息，则直接把用户信息保存到Session中
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//
//        //返回ok
//        return Result.ok();
//    }

    //使用Redis实现
    @Override
    public Result sendCode(String phone, HttpSession session, HttpServletRequest request) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合规范，则返回错误信息
            return Result.fail("手机号格式错误！");
        }

        //3.获取验证码锁
        String getCodeLock = stringRedisTemplate.opsForValue().get(RedisConstants.GET_CODE_LOCK + phone);
        if(getCodeLock != null){
            //4.如果存在锁，则返回错误信息
            return Result.fail("获取验证码过快，请稍后重试！");
        }

        //5.获取IP地址
        String clientIpAddr = request.getRemoteAddr();

        //6. ===== 手机号限流（1 小时最多 15 次）=====
        String phoneKey = RedisConstants.GET_BLACK_LIST_LOCK_PHONE + phone;
        //先让获取次数自增1
        Long phoneCount = stringRedisTemplate.opsForValue().increment(phoneKey);
        //仅第一次获取时设置过期时间为1小时
        if (phoneCount == 1) {
            stringRedisTemplate.expire(phoneKey, RedisConstants.BLACK_LIST_TTL, TimeUnit.HOURS);
        }
        if (phoneCount > 15) {
            return Result.fail("获取验证码次数过多，您的手机号已被限制！");
        }

        //7. ===== IP 限流（1 小时最多 20 次）=====
        String ipKey = RedisConstants.GET_BLACK_LIST_LOCK_CLIENT_IPADDR + clientIpAddr;
        Long ipCount = stringRedisTemplate.opsForValue().increment(ipKey);
        if (ipCount == 1) {
            stringRedisTemplate.expire(ipKey, RedisConstants.BLACK_LIST_TTL, TimeUnit.HOURS);
        }
        if (ipCount > 20) {
            return Result.fail("获取验证码次数过多，您的IP地址已被限制！");
        }

        //8.如果手机号和IP地址都未被限流，则生成验证码并设置验证码锁，防止短时间内重复获取验证码
//        String code = RandomUtil.randomNumbers(6);
        String code = "123456";     //便于测试
        stringRedisTemplate.opsForValue().set(RedisConstants.GET_CODE_LOCK + phone, "1", RedisConstants.CODE_BLOCK_TTL, TimeUnit.MINUTES);

        //9.保存验证码到Redis中，并设置有效期
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //10.发送验证码
        // TODO 调用短信服务发送验证码
        log.debug("发送验证码成功：{}", code);

        //11.返回ok
        return Result.ok();
    }

    //使用Redis实现
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号，如果不符合规范，则返回错误信息
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }

        //校验验证码，如果验证码不正确，则返回错误信息
        String code = loginForm.getCode();
        //从Redis中取出保存的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone).toString();
        //校验验证码是否一致
        if(!cacheCode.equals(code) || cacheCode == null){
            return Result.fail("验证码错误！");
        }

        //根据手机号从数据库查找用户信息   select * from user where phone = ?
        User user = query().eq("phone", phone).one();

        //如果不存在用户信息，则执行注册，将用户信息保存到数据库，并将用户信息保存在Redis中
        if(user == null){
            //注册用户
            user = createUserWithPhone(phone);
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //如果存在用户信息，则直接把用户信息保存到Redis中，这里将用户转成HashMap后再存入
//        Map<String, Object> mapUser = BeanUtil.beanToMap(userDTO);
        Map<String, Object> mapUser = new HashMap<>();
        mapUser.put("id", userDTO.getId().toString());
        mapUser.put("nickName", userDTO.getNickName());
        mapUser.put("icon", userDTO.getIcon());

        String token = UUID.randomUUID().toString(true);

        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, mapUser);
        //设置有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //返回ok
        return Result.ok(token);    //这里将token进行返回，方便后续通过token查找Redis中的用户
    }

    private User createUserWithPhone(String phone) {
        //注册用户
        User user = new User();
        user.setPhone(phone)
                .setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        //保存用户到数据库
        save(user);
        return user;
    }

    //使用BitMap实现签到
    @Override
    public Result sign() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        //更新日期格式
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        //签到（即更新Redis数据）
        //先获取当前是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        //返回ok
        return Result.ok();
    }

    //统计连续签到天数
    @Override
    public Result signCount() {
        //先获取当前用户从第一天到当天的签到记录   BITFIELD key get u[number] offset
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        //更新日期格式
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        //签到（即更新Redis数据）
        //先获取当前是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //BITFIELD key get u[number] offset
        List<Long> list = stringRedisTemplate.opsForValue().bitField(key, 
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        //签到记录是一个十进制的数据，对其进行如下处理来统计签到情况
        if(list == null || list.isEmpty()){
            return Result.ok(0);
        }

        Long num = list.get(0);

        if(num == null || num == 0){
            return Result.ok(0);
        }

        int count = 0;
        while(true){
            //首先将其与1进行与运算，即可得到最后一位为1还是0
            //如果为0则说明该天未签到，直接返回
            if((num & 1) == 0){
                break;
            }else{
                //如果为1则说明当天签到，计数器++并将该数据右移一位，继续统计前一天的签到情况
                count++;
                num = num >> 1;
            }
        }
        //返回签到天数
        return Result.ok(count);
    }
}
