package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.KEY_PREFIX;

public class SimpleRedisLock implements ILock{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //  锁名称
    private String name;

    String key = KEY_PREFIX + name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //  获取线程标识
        long threadID = Thread.currentThread().getId();
        //  获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadID + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //  释放锁
        stringRedisTemplate.delete(key);
    }
}
