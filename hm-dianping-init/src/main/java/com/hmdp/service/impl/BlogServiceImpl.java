package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScorllResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //  查询Blog
        Blog blog = getById(id);
        if (blog == null) {
            Result.fail("笔记不在在");
        }
        //  查询Blog相关用户
        queryBlogUser(blog);
        //  查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userID = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        //  获取登录用户
        Long userID = UserHolder.getUser().getId();
        //  判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());
        if (score == null) {    //  因为是包装类 可能返回空 所以用huto工具类来判断
            //  未点赞
            //  数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //  保存用户到redis的set集合    zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key,userID.toString(),System.currentTimeMillis());
            }
        }else{
            //  点过赞
            //  数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //  把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key,userID.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //  查询Top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //  解析出用户ID
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        //  根据用户ID查询用户 需要倒序
        //  WHERE id IN (5, 1) ORDER BY FIELD (id, 5, 1);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD (id, "+ idStr +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //  返回 UserDTO
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if (!success) {
            return Result.fail("新增笔记失败");
        }
        //  查询作者所有粉丝 select * from tb_follow where follow_user_id = ?;
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //  推送笔记ID给所有粉丝
        for (Follow follow : follows) {
            //  获取粉丝ID
            Long userID = follow.getId();
            //  推送收件箱
            String key = FEED_KEY + userID;
            stringRedisTemplate.opsForSet().add(key, blog.getId().toString(), String.valueOf(System.currentTimeMillis()));
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //  获取当前用户
        Long userID = UserHolder.getUser().getId();
        //  查询收件箱
        String key = FEED_KEY + userID;
        //  分页查询 ZREVRANGEBYSCORE key max min limit offset count
        //  解析数据 blogID minTime (时间戳) offset
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //  获取ID
            ids.add(Long.valueOf(tuple.getValue()));
            //  获取分数 时间戳
            long time = tuple.getScore().longValue();
            if (time == minTime){
                os++;
            } else {
                minTime = time;
            }
        }
        //  根据ID查询Blog
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id, )" + idStr + ")").list();

        //  获取Blog用户信息和点赞数
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        //  封装并返回
        ScorllResult result = new ScorllResult();
        result.setList(blogs);
        result.setOffset(os);
        result.setMinTime(minTime);
        return Result.ok(result);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
