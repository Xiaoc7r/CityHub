-- 滑动窗口限流
-- KEYS[1] 限流 key
-- ARGV[1] 窗口大小（秒）
-- ARGV[2] 窗口内最大请求数
-- ARGV[3] 当前时间戳（毫秒）
local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 移除窗口外的时间戳
redis.call('zremrangebyscore', key, 0, now - window * 1000)

-- 当前窗口内请求数
local current = redis.call('zcard', key)
if current < limit then
    redis.call('zadd', key, now, now .. '-' .. math.random(100000, 999999))
    redis.call('expire', key, window)
    return 1
end
return 0
