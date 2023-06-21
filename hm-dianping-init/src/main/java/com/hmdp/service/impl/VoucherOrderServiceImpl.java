package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Resource
    private RedissonClient redissonClient;


    //  创建线程池
    public static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init (){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    //  阻塞队列
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.order";
        @Override
        public void run() {
            while (true){
                try {
                    //  获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //  判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //  失败 没有消息 继续下一次循环
                        continue;
                    }
                    //  解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //  成功 下单
                    handleVoucherOrder(voucherOrder);
                    //  ACK 确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                    if(proxy == null){
                        continue;
                    }
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    //  获取PendingList中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName,ReadOffset.from("0"))
                    );
                    //  判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //  失败 PendingList没有消息 结束循环
                        break;
                    }
                    //  解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //  成功 下单
                    handleVoucherOrder(voucherOrder);
                    //  ACK 确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    log.error("处理PendingList订单异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
    }
    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //  获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //  创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //  获取用户
        Long userID = voucherOrder.getId();
        //  创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userID);
        //  尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //  获取失败
            log.error("不允许重复下单");
            return ;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherID) {
        //  获取用户
        Long userID = UserHolder.getUser().getId();
        //  获取订单ID
        long orderID = redisIDWorker.nextID("order");
        //  执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherID.toString(), userID.toString(), String.valueOf(orderID)
        );
        int r = result.intValue();
        //  判断结果为0
        if (r != 0) {
            //  不为0 但没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //  为0 有购买资格 把下单信息保存到阻塞队列
        //  获取代理对象 (事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //  返回订单ID
        return Result.ok(orderID);
    }

    /*
    //  Redis模拟 死信队列
    public Result seckillVoucher(Long voucherID) {
        //  获取用户
        Long userID = UserHolder.getUser().getId();
        //  执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherID.toString(), userID.toString()
        );
        int r = result.intValue();
        //  判断结果为0
        if (r != 0) {
            //  不为0 但没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //  为0 有购买资格 把下单信息保存到阻塞队列
        long orderID = redisIDWorker.nextID("order");
        //  保存阻塞队列
        //  订单ID
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderID);
        //  用户ID
        voucherOrder.setUserId(userID);
        //  代金券ID
        voucherOrder.setVoucherId(voucherID);
        //  放入阻塞队列
        orderTasks.add(voucherOrder);
        //  获取代理对象 (事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //  返回订单ID
        return Result.ok(orderID);
    }
     */

    /*
    //  下面是没有使用lua脚本的业务代码
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
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "lock:order:" + userID);
        RLock lock = redissonClient.getLock("lock:order:" + userID);
        //  尝试获取锁
        boolean isLock = lock.tryLock();
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
//    */
    @Transactional  //  加上这个出现问题会回滚
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //  一人一单
        Long userID = UserHolder.getUser().getId();
        //  使用锁方法使每一个用户ID都有独立的锁 降低性能消耗     intern方法从字符串常量池内找和你值一样的字符串地址
        //  查询订单
        int count = query().eq("user_id", userID).eq("voucher_id", voucherOrder).count();
        //  判断用户是否存在订单
        if (count > 0) {
            //  用户已经购买过了
            Result.fail("用户已经购买过了!");
        }
        //  扣减库存
        boolean success = seckillVoucherService
                .update().setSql("stock = stock - 1")   //  set stock = stock -1
                .eq("voucher_id", voucherOrder)
                .gt("stock",0) //  where id = ? and stock = ?
                .update();
        if (!success){
            //  扣减失败
            Result.fail("库存不足 !");
        }
        //  创建订单
        save(voucherOrder);
    }
}
