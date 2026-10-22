"""Read-only deployment checks. Run explicitly on the owned VPS as Docker administrator."""
import json
import subprocess
import unittest

def run(*args, check=True, data=None):
    return subprocess.run(args, input=data, text=True, capture_output=True, check=check, timeout=30)

class DeploymentTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.containers = {}
        for name in ('app', 'waf', 'db'):
            cls.containers[name] = json.loads(run('docker', 'inspect', 'dragon-java-' + name + '-1').stdout)[0]

    def test_01_vpn_unchanged(self):
        result = run('python3', '/opt/dragon-list/current/deploy/vpn_guard.py', 'verify', '--file', '/var/lib/dragon-list-java/vpn-before.json')
        self.assertIn('passed', result.stdout)

    def test_02_no_published_backend_ports(self):
        for container in self.containers.values():
            self.assertFalse(container['HostConfig']['PortBindings'])
            self.assertFalse(container['HostConfig']['Privileged'])
            self.assertNotEqual(container['HostConfig']['NetworkMode'], 'host')

    def test_03_process_isolation(self):
        for name, container in self.containers.items():
            self.assertIn(container['Config']['User'], ('10001:10001', '1000:1000', '70:70'))
            self.assertIn('ALL', container['HostConfig']['CapDrop'])
            self.assertTrue(any(('no-new-privileges' in x for x in container['HostConfig']['SecurityOpt'])))
            if name != 'db':
                self.assertTrue(container['HostConfig']['ReadonlyRootfs'])

    def test_04_network_separation(self):
        networks = {k: set(v['NetworkSettings']['Networks']) for k, v in self.containers.items()}
        self.assertEqual(networks['db'], {'dragon-java_database'})
        self.assertEqual(networks['app'], {'dragon-java_backend', 'dragon-java_database'})
        self.assertEqual(networks['waf'], {'dragon-java_backend', 'dragon-list_default'})
        for name in ('dragon-java_database', 'dragon-java_backend'):
            self.assertTrue(json.loads(run('docker', 'network', 'inspect', name).stdout)[0]['Internal'])

    def test_05_database_least_privilege(self):
        prefix = ('docker', 'exec', 'dragon-java-db-1', 'psql', '-U', 'postgres', '-d', 'dragonlist', '-At', '-v', 'ON_ERROR_STOP=1', '-c')
        sql = "SELECT rolsuper,rolcreatedb,rolcreaterole,rolreplication FROM pg_roles WHERE rolname='dragon_app'"
        self.assertEqual(run(*prefix, sql).stdout.strip(), 'f|f|f|f')
        sql = "SELECT has_schema_privilege('dragon_app','public','CREATE'),has_table_privilege('dragon_app','users','SELECT'),has_table_privilege('dragon_app','flyway_schema_history','SELECT')"
        self.assertEqual(run(*prefix, sql).stdout.strip(), 'f|t|f')
        for operation in ('CREATE TABLE public.dragon_permission_probe(id int)', 'ALTER TABLE public.users ADD COLUMN dragon_permission_probe int'):
            result = run(*prefix, 'BEGIN; SET LOCAL ROLE dragon_app; ' + operation + '; ROLLBACK;', check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('permission', result.stderr.lower() + ('permission' if 'must be owner' in result.stderr else ''))

    def test_06_resource_limits(self):
        for container in self.containers.values():
            host = container['HostConfig']
            self.assertGreater(host['Memory'], 0)
            self.assertLessEqual(host['Memory'], 1024 ** 3)
            self.assertGreater(host['NanoCpus'], 0)
            self.assertEqual(host['PidsLimit'], 128)
            self.assertEqual(host['LogConfig']['Config']['max-size'], '10m')

    def test_07_no_host_control_or_backup_mount(self):
        for name, container in self.containers.items():
            for mount in container['Mounts']:
                self.assertNotIn('docker.sock', mount.get('Source', ''))
                self.assertFalse(mount.get('Source', '').startswith('/var/backups'))
            if name == 'app':
                self.assertEqual(container['Mounts'], [])
        self.assertEqual(run('docker', 'exec', 'dragon-java-app-1', 'sh', '-c', 'ls -1 /app').stdout.strip(), 'app.jar')

    def test_08_backups_and_old_container_preserved(self):
        from pathlib import Path
        dumps = list(Path('/var/backups/dragon-list-java').glob('*.dump'))
        self.assertTrue(dumps)
        self.assertEqual(dumps[-1].stat().st_mode & 63, 0)
        run('docker', 'inspect', '-f', '{{.Id}}', 'dragon-list-app-1')
        run('systemctl', 'is-enabled', 'dragon-list-java-backup.timer')
        self.assertTrue(Path('/var/lib/dragon-list-java/restore-check.json').exists())
if __name__ == '__main__':
    unittest.main(verbosity=2)
