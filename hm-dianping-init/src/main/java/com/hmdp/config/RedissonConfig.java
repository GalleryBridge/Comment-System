package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //  配置
        Config config = new Config();              //8.139.7.184
        config.useSingleServer().setAddress("redis://8.139.7.184:6379").setPassword("001011");
        //  创建RedissonClient对象
        return Redisson.create(config);
    }
}
