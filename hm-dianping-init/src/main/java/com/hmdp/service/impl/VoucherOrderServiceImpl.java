package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import jdk.jfr.consumer.RecordedThread;
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
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Override
    @Transactional  //  加上这个出现问题会回滚
    public Result seckillVoucher(Long voucherId) {
        //  查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
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
        //  扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
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
        Long userID = UserHolder.getUser().getId();
        voucherOrder.setUserId(userID);
        //  代金券id
        voucherOrder.setVoucherId(voucherId);
        return Result.ok(orderID);
    }
}
