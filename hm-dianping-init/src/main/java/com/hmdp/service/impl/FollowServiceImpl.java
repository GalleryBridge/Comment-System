package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result follow(Long followUserID, Boolean isFollow) {
        //  获取用户ID
        Long userID = UserHolder.getUser().getId();
        String key =  FOLLOWS_KEY + userID;
        //  判断关注还是取关
        if (isFollow){
            //  关注 新增数据
            Follow follow = new Follow();
            follow.setUserId(userID);
            follow.setFollowUserId(followUserID);
            boolean Success = save(follow);
            if (Success) {
                //  把关注用户的id放入redis的set集合 sadd userID followerUseID
                stringRedisTemplate.opsForSet().add(key,followUserID.toString());
            }
        }else {
            //  取关 删除   delete from tb_follow where userID = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userID).eq("follow_user_id", followUserID));
            if (isSuccess) {
                //  把关注的用户ID从Reids中移除
                stringRedisTemplate.opsForSet().remove(key, followUserID.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserID) {
        Long userID = UserHolder.getUser().getId();
        //  查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?;
        Integer count = query().eq("user_id", userID).eq("follow_user_id", followUserID).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //  获取当前用户ID 和目标用户ID
        Long userID = UserHolder.getUser().getId();
        String key1 = "follows:" + userID;
        String key2 = "follows:" + id;
        //  取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        //  解析ID集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //  查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
