#!/bin/sh
set -eu
umask 077
backup_dir=/var/backups/dragon-list-java
mkdir -p "$backup_dir"
exec 9>/run/lock/dragon-list-java-backup.lock
flock -n 9 || exit 0
stamp=$(date -u +%Y%m%dT%H%M%SZ)
temporary="$backup_dir/$stamp.dump.partial"
trap 'rm -f "$temporary"' EXIT HUP INT TERM
docker exec dragon-java-db-1 pg_dump -U postgres -d dragonlist -Fc > "$temporary"
docker exec -i dragon-java-db-1 pg_restore --list < "$temporary" > /dev/null
mv "$temporary" "$backup_dir/$stamp.dump"
# Delete only this application's matching dumps after 14 days.
find "$backup_dir" -maxdepth 1 -type f -name '????????T??????Z.dump' -mtime +14 -delete
printf 'Dragon List backup completed %s\n' "$stamp"
