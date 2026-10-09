redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'role', ARGV[2], 'status', ARGV[3])
redis.call('EXPIRE', KEYS[1], ARGV[5])
redis.call('SADD', KEYS[2], ARGV[4])
redis.call('EXPIRE', KEYS[2], ARGV[5])
return 1
