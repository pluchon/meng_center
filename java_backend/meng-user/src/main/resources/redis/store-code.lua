redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'errors', '0')
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1
