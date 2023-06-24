package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //  验证手机号
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //  调用验证工具类验证手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码格式错误");
        }
        //  生成手机验证码 利用 hutool 工具类
        String code = RandomUtil.randomNumbers(6);

        //  验证码保存Session
//        session.setAttribute("code",code);
        //  验证码保存Redis  //  set value  ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //  发送验证码
        log.debug("发送验证码成功, 验证码:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //  校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式错误");
        }
        //  从Redis中获取验证码
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //  验证码错误
        if (cacheCode == null || !cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        //  验证码正确 根据手机查询用户
        User user = query().eq("phone", phone).one();
        //  判断用户是否存在
        if (user == null) {
            //  用户不存在 保存在数据库中
            user = createUserWithPhone(phone);
        }
        //  保存用户信息到Redis中
        String token = UUID.randomUUID().toString(true);
        //  将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //  存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //  设置有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(LoginFormDTO loginForm, HttpSession session) {
//        stringRedisTemplate.delete(LOGIN_USER_KEY);
//        String token = session.getAttribute("token").toString();
//        System.out.println(token);
//        session.removeAttribute(LOGIN_USER_KEY+token);
//        stringRedisTemplate.delete(LOGIN_USER_KEY+token);
        return null;
    }

    private User createUserWithPhone(String phone){
        //  创建user对象 填充非空属性
        User user = new User();
        user.setPhone(phone);
        //  生成随你昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //  保存到数据库
        save(user);
        return user;
    }
}
