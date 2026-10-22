"""Run against a deployment you own. Test accounts are explicitly prefixed and isolated."""
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
import time

class Report(unittest.TextTestResult):

    def startTest(self, test):
        super().startTest(test)
        self.started = time.monotonic()

    def addSuccess(self, test):
        super().addSuccess(test)
        self.records.append((str(test), 'passed', '', time.monotonic() - self.started))

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.records.append((str(test), 'failure', self._exc_info_to_string(err, test), time.monotonic() - self.started))

    def addError(self, test, err):
        super().addError(test, err)
        self.records.append((str(test), 'error', self._exc_info_to_string(err, test), time.monotonic() - getattr(self, 'started', time.monotonic())))

    def addSkip(self, test, reason):
        super().addSkip(test, reason)
        self.records.append((str(test), 'skipped', reason, 0))

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.records = []
suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent), pattern='test_*.py')
result = unittest.TextTestRunner(verbosity=2, resultclass=Report).run(suite)
root = ET.Element('testsuite', tests=str(result.testsRun), failures=str(len(result.failures)), errors=str(len(result.errors)), skipped=str(len(result.skipped)))
for name, status, message, elapsed in result.records:
    node = ET.SubElement(root, 'testcase', name=name, time=f'{elapsed:.3f}')
    if status != 'passed':
        ET.SubElement(node, status).text = message
out = Path('.work/reports')
out.mkdir(parents=True, exist_ok=True)
ET.ElementTree(root).write(out / 'python-http.xml', encoding='utf-8', xml_declaration=True)
sys.exit(0 if result.wasSuccessful() else 1)
