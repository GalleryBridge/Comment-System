package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private final  StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //  写入Redis
    public void set (String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //  逻辑过期
    public void setWithLogicalExpire (String key, Object value, Long time, TimeUnit unit){

        //  设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //  写入 Reids
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    //  解决缓存穿透
    //  Function <参数类型, 返回值类型>
    public <R,ID> R queryWithPassThrough(String keyPrefixLong, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        //  从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //  判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //  存在直接返回
            return JSONUtil.toBean(json, type);
        }
        //  判断是否为空值
        if (json != null) {
            //  返回错误信息
            return null;
        }
        //  不存在根据id查询数据库    传入方法
        R r = dbFallBack.apply(id);
        //  不存在 返回错误404
        if (r == null) {
            //  将空值写入Redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //  存在写入redis
        this.set(CACHE_SHOP_KEY + id,r,time,unit);
        //  返回
        return r;
    }
    //  解决缓存击穿
}
