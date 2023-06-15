package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jdk.jfr.consumer.RecordedThread;
import org.apache.ibatis.mapping.ResultFlag;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Override
    public Result seckillVoucher(Long voucherID) {
        //  查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherID);
        //  判断秒杀是否开始
        //  开始时间在当前时间之后
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //  未开始
            return Result.fail("秒杀尚未开始 !");
        }
        //  判断秒杀是否结束
        //  结束时间在当前时间之前
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //  已结束
            return Result.fail("秒杀已经结束 !");
        }
        //  判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足 !");
        }

        Long userID = UserHolder.getUser().getId();
        //  创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "oring:" + userID);
        //  尝试获取锁
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            //  获取失败
            return Result.fail("一个人只允许下一单");
        }
        try {
            //  获取代理对象 (事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherID);
        } finally {
            lock.unlock();
        }
        /**
         *   锁加在这里的原因:
         *   如果加在 createVoucherOrder 方法名 左边 public synchronized Result createVoucherOrder
         *       会导致全部的用户对象都调用一个锁 效率低
         *   如果加载 createVoucherOrder 方法中
         *       虽然解决了每个用户都有一把锁 但作用域太小 会导致 释放了锁还没有提交到数据库中 另一个线程就进来了
         *   将锁加载 createVoucherOrder 方法外
         *       当事务提交结束后才会释放锁 保证了数据安全 但并没有由事务代理对象
         *       使用接口 AopContext.currentProxy() 获取代理对象
         *       ** 补习Spring 事务 **
         */
      /*  Long userID = UserHolder.getUser().getId();
        synchronized (userID.toString().intern()) {
            //  获取代理对象 (事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherID);
        }*/
    }

    @Transactional  //  加上这个出现问题会回滚
    public Result createVoucherOrder(Long voucherID) {
        //  一人一单
        Long userID = UserHolder.getUser().getId();
        //  使用锁方法使每一个用户ID都有独立的锁 降低性能消耗     intern方法从字符串常量池内找和你值一样的字符串地址
        //  查询订单
        int count = query().eq("user_id", userID).eq("voucher_id", voucherID).count();
        //  判断用户是否存在订单
        if (count > 0) {
            //  用户已经购买过了
            return Result.fail("用户已经购买过了!");
        }
        //  扣减库存
        boolean success = seckillVoucherService
                .update().setSql("stock = stock - 1")   //  set stock = stock -1
                .eq("voucher_id", voucherID)
                .gt("stock",0) //  where id = ? and stock = ?
                .update();
        if (!success){
            //  扣减失败
            return Result.fail("库存不足 !");
        }
        //  创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //  订单id
        long orderID = redisIDWorker.nextID("order:");
        voucherOrder.setId(orderID);
        //  用户id
        voucherOrder.setUserId(userID);
        //  代金券id
        voucherOrder.setVoucherId(voucherID);
        save(voucherOrder);
        //  TODO 悲观锁结束
        return Result.ok(orderID);

    }
}
