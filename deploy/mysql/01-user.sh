#!/bin/bash
set -euo pipefail

# Hex encoding avoids SQL/shell interpolation of credentials. No password output.
password_hex=$(printf '%s' "$MENG_MYSQL_USER_PASSWORD" | od -An -v -tx1 | tr -d ' \n')
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS meng_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET @app_password = CONVERT(0x${password_hex} USING utf8mb4);
SET @create_user = CONCAT('CREATE USER IF NOT EXISTS ''meng_user_app''@''%'' IDENTIFIED BY ', QUOTE(@app_password));
PREPARE create_user FROM @create_user;
EXECUTE create_user;
DEALLOCATE PREPARE create_user;
GRANT ALL PRIVILEGES ON meng_user.* TO 'meng_user_app'@'%';
SQL
unset password_hex
