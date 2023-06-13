package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    //  开始时间时间戳
    public static final long BEGIN_TIMESTAMP = 1672531200L;
    //  序列号位数
    public static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //  获取开始时间戳
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }

    public long nextID(String keyPrefix){
        //  生成时间戳 当前时间
        LocalDateTime now = LocalDateTime.now();
        //  转换成秒
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //  生成序列号 自增长
        //  获取当前日期 精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //  Redis自增长有上限 2^6
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //  拼接并返回 借助位运算 返回long类型
        //  将 timestamp 左移常量值的位数 然后用或运算拼接 count
        return timestamp << COUNT_BITS | count;
    }
}
