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
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Override
    public Result sign() {
        //  获取当前登录用户
        Long userID = UserHolder.getUser().getId();
        //  获取日期
        LocalDateTime now = LocalDateTime.now();
        //  拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userID + keySuffix;
        //  获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //  写入Redis SETBIT KEY OFFSET 0/1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //  获取当前登录用户
        Long userID = UserHolder.getUser().getId();
        //  获取日期
        LocalDateTime now = LocalDateTime.now();
        //  拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userID + keySuffix;
        //  获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //  获取本月截至今天为止的所有签到记录 返回一个十进制数字 BITFIELD sign:1013:202306 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            //  没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        System.out.println("num = " + num);
        //  循环遍历
        int count = 0;
        while (true) {
            //  让这个数字与1做运算 得到最后一个Bit位
            //  判断这个位是否为0
            if ((num & 1) == 0) {
                //  为0 说明未签到 结束
                break;
            } else {
                //  为1 说明签到了 计数器+1
                count++;
            }
            //  把数字右移一位 抛弃最后一个Bit位 继续下一个Bit位
            num >>>= 1;
        }
        System.out.println("count = " + count);
        return Result.ok(count);
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
