local stored = redis.call('HGET', KEYS[1], 'hash')
if not stored then return 0 end
if stored == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
local errors = redis.call('HINCRBY', KEYS[1], 'errors', 1)
if errors >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1]) end
return 0
