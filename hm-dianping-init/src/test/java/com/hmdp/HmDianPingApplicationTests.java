package com.hmdp;

import cn.hutool.db.nosql.redis.RedisDS;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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

    @Test
    void loadShopData(){
        //  查询店铺信息
        List<Shop> list = shopService.list();
        //  把店铺分组 按照TypeID分组 TypeID相同的放在一个组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //  分批存储
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //  获取类型ID
            Long typeID = entry.getKey();
            String key = SHOP_GEO_KEY + typeID;
            //  获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //  写入reids GEOADD key 经度纬度 member
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);  // GeoLocation<M> location
        }
    }
}
