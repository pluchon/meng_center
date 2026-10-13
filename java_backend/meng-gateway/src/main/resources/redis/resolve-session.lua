local userId = redis.call('HGET', KEYS[1], 'userId')
local role = redis.call('HGET', KEYS[1], 'role')
local status = redis.call('HGET', KEYS[1], 'status')
if userId ~= ARGV[1] or not role or status ~= 'NORMAL' then return '' end
local now = tonumber(redis.call('TIME')[1])
local active = tonumber(redis.call('HGET', KEYS[1], 'lastActiveAt') or '0')
if now - active >= 300 then redis.call('HSET', KEYS[1], 'lastActiveAt', tostring(now)) end
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('EXPIRE', KEYS[2], ARGV[2])
return role
