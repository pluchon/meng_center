#!/bin/sh
set -eu

# ACL stores a password hash; the plaintext never enters argv or a config file.
password_hash=$(printf '%s' "$MENG_REDIS_PASSWORD" | sha256sum | cut -d ' ' -f 1)
umask 077
printf 'user default on #%s ~* &* +@all\n' "$password_hash" > /tmp/meng-users.acl
chown redis:redis /tmp/meng-users.acl
unset password_hash
exec /usr/local/bin/docker-entrypoint.sh redis-server --appendonly yes --aclfile /tmp/meng-users.acl
