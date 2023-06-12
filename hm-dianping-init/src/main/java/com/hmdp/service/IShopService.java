package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//  Ctrl + Alt + B 进入实现类
public interface IShopService extends IService<Shop> {

    Result queryByID(Long id);

    Result update(Shop shop);
}
