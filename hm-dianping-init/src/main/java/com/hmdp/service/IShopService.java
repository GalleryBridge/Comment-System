package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
//  Ctrl + Alt + T 方法T
//  Ctrl + Alt + B 进入实现类
//  Ctrl + Alt + M 封装函数
public interface IShopService extends IService<Shop> {

    Result queryByID(Long id);

    Result update(Shop shop);

    Result queryShopByTypeID(Integer typeId, Integer current, Double x, Double y);
}
