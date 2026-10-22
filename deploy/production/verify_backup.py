"""Restore the latest owned deployment backup into a disposable database on the VPS."""
import datetime
import json
from pathlib import Path
import subprocess
import uuid


def execute(*arguments, data=None):
    return subprocess.run(arguments, input=data, capture_output=True, check=True, timeout=120)


def sql(database, statement):
    return execute(
        'docker', 'exec', 'dragon-java-db-1', 'psql', '-U', 'postgres', '-d', database,
        '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement,
    ).stdout.decode().strip()


def main():
    backups = sorted(Path('/var/backups/dragon-list-java').glob('*.dump'))
    if not backups:
        raise SystemExit('No backups found')
    backup = backups[-1]
    database = 'dragon_verify_' + uuid.uuid4().hex
    execute('docker', 'exec', 'dragon-java-db-1', 'createdb', '-U', 'postgres', database)
    try:
        execute(
            'docker', 'exec', '-i', 'dragon-java-db-1', 'pg_restore',
            '-U', 'postgres', '-d', database, '--exit-on-error', data=backup.read_bytes(),
        )
        counts = sql(database, 'SELECT (SELECT COUNT(*) FROM users),'
                     '(SELECT COUNT(*) FROM notes),(SELECT COUNT(*) FROM media)')
        if sql(database, 'SELECT COUNT(*) FROM flyway_schema_history WHERE success') != '3':
            raise RuntimeError('Unexpected restored schema version')
        record = {
            'dump': backup.name, 'counts': counts, 'restored': True,
            'checked_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        }
        Path('/var/lib/dragon-list-java/restore-check.json').write_text(json.dumps(record))
        print('Backup restored successfully in disposable database')
    finally:
        # Only the database created by this invocation is removed; the live DB is never targeted.
        execute('docker', 'exec', 'dragon-java-db-1', 'dropdb', '-U', 'postgres', database)


if __name__ == '__main__':
    main()
