-- stock.lua
for i = 1, #KEYS do
    local key = KEYS[i]
    local val = redis.call('get', key)

    -- 打印日志（可以在 Redis 控制台用 MONITOR 查看）
    -- redis.log(redis.LOG_NOTICE, "Checking Key: " .. tostring(key))

    -- 1. 检查 Redis 是否有值
    if val == false or val == nil then
        return -1
    end

    -- 2. 获取 Java 传来的扣减数量
    local arg_val = ARGV[i]
    if arg_val == nil then
        return -1 -- 参数传递缺失
    end

    -- 3. 类型转换
    local stock = tonumber(val)
    local need = tonumber(arg_val)

    -- 4. 比较（确保两端都不为 nil）
    if stock == nil or need == nil or stock < need then
        return -1
    end
end

-- 全过之后再扣减
for i = 1, #KEYS do
    redis.call('decrby', KEYS[i], ARGV[i])
end

return 1