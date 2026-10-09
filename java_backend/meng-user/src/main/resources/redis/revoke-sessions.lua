local sessions = redis.call('SMEMBERS', KEYS[1])
for _, id in ipairs(sessions) do redis.call('DEL', ARGV[1] .. id) end
redis.call('DEL', KEYS[1])
return #sessions
