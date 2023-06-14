package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.management.timer.TimerMBean;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    //  线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    StringRedisTemplate stringRedisTemplate;

    //  使用工具类
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryByID(Long id) {
        //  缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //  TODO 工具类有Bug 没找
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //  互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        //  逻辑过期解决缓存击穿  ** 需要先缓存预热 就是提前缓存好 在Test文件夹下**
//        Shop shop = queryWithLogicalExpire(id);
//        System.out.println("商品实现类中调用方法返回的商品数据"+shop);
        if (shop == null) {
            Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    //  缓存击穿
    public Shop queryWithMutex(Long id){
        //  从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //  判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //  存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //  判断是否为空值
        if (shopJson != null) {
            //  返回错误信息
            return null;
        }
        //  TODO 实现缓存重建
        //  TODO 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
//      <== Ctrl + Alt + 6 大异常 ==>
        try {
            boolean isLock = tryLock(lockKey);
            //  TODO 判断是否获取成功
            if (!isLock) {
                //  失败 休眠并重试
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            //  不存在根据id查询数据库
            shop = getById(id);
            //  不存在 返回错误404
            if (shop == null) {
                //  将空值写入Redis
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //  存在写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //  TODO 释放锁
            unlock(lockKey);
        }
        //  返回
        return shop;
    }

    //  逻辑过期
    public Shop queryWithLogicalExpire(Long id){
        //  从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //  判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //  存在直接返回
            return null;
        }
        //  TODO 命中需要判断过期时间 需要先把Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //  获取店铺信息
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //  获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //  TODO 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){   //  过期时间是否在当前时间之后
            //  TODO 未过期返回信息
            System.out.println("商品实现类中更新前的商品数据"+shop);
            return shop;
        }
        //  TODO 过期 需要缓存重建
        //  TODO 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //  TODO 成功 开启独立线程 实现缓存重建   建议使用线程池
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,10L);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //  释放锁
                    unlock(lockKey);
                }
            });
        }
        System.out.println("商品实现类中更新后的商品数据"+shop);
        //  TODO 返回店铺信息
        return shop;

//        //  不存在根据id查询数据库
//        Shop shop = getById(id);
//        //  存在写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    //  逻辑过期时间 重建缓存
    //  没有传过期时间 永久有效
    public void saveShop2Redis(Long id,Long expireSecond){
        //  查询商铺信息
        Shop shop = getById(id);
        //  封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        //  写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    //  缓存穿透    使用工具类后 下面代码就可以删除了
    public Shop queryWithPassThrough(Long id){
        //  从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //  判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //  存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //  判断是否为空值
        if (shopJson != null) {
            //  返回错误信息
            return null;
        }
        //  不存在根据id查询数据库
        Shop shop = getById(id);
        //  不存在 返回错误404
        if (shop == null) {
            //  将空值写入Redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //  存在写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //  返回
        return shop;
    }

    //  获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //  释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //  跟新店铺信息
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能为空!");
        }
        //  更新数据库
        updateById(shop);
        //  删除缓存
        stringRedisTemplate.delete("SHOP" + id);
        return Result.ok();
    }
}
