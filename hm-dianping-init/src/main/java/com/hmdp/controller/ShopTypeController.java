package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
//        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        System.out.println("Controller中输出的类型"+typeService.queryList().getData());
        //  Controller中输出的类型 查询数据库
        //  ZShopType(id=1, name=美食, icon=/types/ms.png, sort=1, createTime=2021-12-22T20:17:47, updateTime=2021-12-23T11:24:31),ShopType(id=2, name=KTV, icon=/types/KTV.png, sort=2, createTime=2021-12-22T20:18:27, updateTime=2021-12-23T11:24:31), ShopType(id=3, name=丽人·美发, icon=/types/lrmf.png, sort=3, createTime=2021-12-22T20:18:48, updateTime=2021-12-23T11:24:31), ShopType(id=10, name=美睫·美甲, icon=/types/mjmj.png, sort=4, createTime=2021-12-22T20:21:46, updateTime=2021-12-23T11:24:31), ShopType(id=5, name=按摩·足疗, icon=/types/amzl.png, sort=5, createTime=2021-12-22T20:19:27, updateTime=2021-12-23T11:24:31), ShopType(id=6, name=美容SPA, icon=/types/spa.png, sort=6, createTime=2021-12-22T20:19:35, updateTime=2021-12-23T11:24:31), ShopType(id=7, name=亲子游乐, icon=/types/qzyl.png, sort=7, createTime=2021-12-22T20:19:53, updateTime=2021-12-23T11:24:31), ShopType(id=8, name=酒吧, icon=/types/jiuba.png, sort=8, createTime=2021-12-22T20:20:02, updateTime=2021-12-23T11:24:31), ShopType(id=9, name=轰趴馆, icon=/types/hpg.png, sort=9, createTime=2021-12-22T20:20:08, updateTime=2021-12-23T11:24:31), ShopType(id=4, name=健身运动, icon=/types/jsyd.png, sort=10, createTime=2021-12-22T20:19:04, updateTime=2021-12-23T11:24:31)]
        //  Controller中输出的类型 查询Redis
        //  Result(success=true, errorMsg=null, data=[
        //  ShopType(id=1, name=美食, icon=/types/ms.png, sort=1, createTime=2021-12-22T20:17:47, updateTime=2021-12-23T11:24:31), ShopType(id=2, name=KTV, icon=/types/KTV.png, sort=2, createTime=2021-12-22T20:18:27, updateTime=2021-12-23T11:24:31), ShopType(id=3, name=丽人·美发, icon=/types/lrmf.png, sort=3, createTime=2021-12-22T20:18:48, updateTime=2021-12-23T11:24:31), ShopType(id=10, name=美睫·美甲, icon=/types/mjmj.png, sort=4, createTime=2021-12-22T20:21:46, updateTime=2021-12-23T11:24:31), ShopType(id=5, name=按摩·足疗, icon=/types/amzl.png, sort=5, createTime=2021-12-22T20:19:27, updateTime=2021-12-23T11:24:31), ShopType(id=6, name=美容SPA, icon=/types/spa.png, sort=6, createTime=2021-12-22T20:19:35, updateTime=2021-12-23T11:24:31), ShopType(id=7, name=亲子游乐, icon=/types/qzyl.png, sort=7, createTime=2021-12-22T20:19:53, updateTime=2021-12-23T11:24:31), ShopType(id=8, name=酒吧, icon=/types/jiuba.png, sort=8, createTime=2021-12-22T20:20:02, updateTime=2021-12-23T11:24:31), ShopType(id=9, name=轰趴馆, icon=/types/hpg.png, sort=9, createTime=2021-12-22T20:20:08, updateTime=2021-12-23T11:24:31), ShopType(id=4, name=健身运动, icon=/types/jsyd.png, sort=10, createTime=2021-12-22T20:19:04, updateTime=2021-12-23T11:24:31)], total=null)
        return Result.ok(typeService.queryList().getData());
    }
}
