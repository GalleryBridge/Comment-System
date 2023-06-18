-- KEYS[1] 锁的key  ARGV[1] 线程标识
-- 比较线程标识和锁的标识是否一样
if(redis.call('GET',KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('DEL',KEYS[1])
end
-- 不一致 直接返回
return 0