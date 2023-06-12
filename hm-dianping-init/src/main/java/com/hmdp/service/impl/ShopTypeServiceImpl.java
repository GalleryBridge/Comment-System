package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        List<String> shopTypeList = new ArrayList<>();
        //  从Redis中查询缓存 参数 key ,从0开始 ,到最后(-1)
        shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY,0,-1);
        //  判断是否存在
        if (!shopTypeList.isEmpty()) {
            List<ShopType> typeList = new ArrayList<>();
            //  存在直接返回
            for (String s:shopTypeList){
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //  不存在根据id查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //  不存在 返回错误404
        if (typeList.isEmpty()) {
            return Result.fail("店铺不存在");
        }
        //  存在 写入redis
        for (ShopType shopType : typeList) {
            String s = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(s);
        }
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,shopTypeList);
        //  返回
        return Result.ok(typeList);
    }
}
