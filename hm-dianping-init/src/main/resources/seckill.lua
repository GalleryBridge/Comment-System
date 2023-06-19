-- 1 参数列表
-- 1.1 优惠券ID
local voucherID = ARGV[1]
-- 1.2 用户ID
local userID = ARGV[2]
-- 1.3 订单ID
local orderID = ARGV[3]

-- 2 数据key
-- 2.1 库存key
local stockKey = 'seckill:stock:' .. voucherID
-- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherID

-- 3 脚本业务
-- 3.1 判断库存是否充足 get stockKey
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2 库存不足 返回1
    return 1
end
-- 3.2 判断用户是否下单 SISMEMBER orderKey userID
if (redis.call("sismember", orderKey,userID) == 1) then
    --  3.3 存在说明重复下单 返回2
    return 2
end
-- 3.4 扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5 下单 (保存用户) sadd orderKey userID
redis.call('sadd', orderKey, userID)
return 0