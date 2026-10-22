#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username postgres --dbname dragonlist --set=migrator_password="$MIGRATOR_PASSWORD" --set=app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE dragon_migrator LOGIN PASSWORD :'migrator_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE dragon_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
ALTER DATABASE dragonlist OWNER TO dragon_migrator;
ALTER SCHEMA public OWNER TO dragon_migrator;
REVOKE ALL ON DATABASE dragonlist FROM PUBLIC;
GRANT CONNECT ON DATABASE dragonlist TO dragon_migrator, dragon_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO dragon_app;
ALTER DEFAULT PRIVILEGES FOR ROLE dragon_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dragon_app;
ALTER DEFAULT PRIVILEGES FOR ROLE dragon_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO dragon_app;
SQL
