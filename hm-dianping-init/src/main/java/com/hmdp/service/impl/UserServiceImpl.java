package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

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
        session.setAttribute("code",code);

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
        //  校验验证码
        Object cacheCode = session.getAttribute("code");
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
        //  保存用户信息到session中
        session.setAttribute("user",user);
        return Result.ok();
    }

    private User createUserWithPhone(String phone){
        //  创建user对象 填充非空属性
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //  保存到数据库
        save(user);
        return user;
    }
}
