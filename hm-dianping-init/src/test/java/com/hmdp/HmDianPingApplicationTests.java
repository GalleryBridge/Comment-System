package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //  线程池
    private ExecutorService es = Executors.newFixedThreadPool(50);

//    @Test
//    void saveShop(){
//        shopService.saveShop2Redis(1L,10L);
//    }

    @Test
    void testIDWorker() throws InterruptedException{
        long order = redisIDWorker.nextID("order");
        System.out.println("order = " + order);
        //  order = 60895364877647873

        /*
        CountDownLatch latch = new CountDownLatch(50);
        Runnable task = () -> {
            for (int i = 0; i < 20; i++) {
                long order = redisIDWorker.nextID("order");
                System.out.println("order = " + order);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        //  提交
        for (int i = 0; i < 60; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time:"+ (begin - end));*/
    }


}
