package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByID(Long id) {
        //  缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //  互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
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

    //  缓存穿透
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
