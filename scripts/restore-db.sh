#!/bin/sh
set -eu

target_database=$1
backup_file=$2
table_count=$(MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${target_database}'")
if [ "$table_count" -ne 0 ]; then
    echo "Target database exists and is not empty" >&2
    exit 2
fi

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE IF NOT EXISTS \`${target_database}\` CHARACTER SET utf8mb4"
gzip -dc "$backup_file" | MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$target_database"
