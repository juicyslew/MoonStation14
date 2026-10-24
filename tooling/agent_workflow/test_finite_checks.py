import json
import tempfile
import unittest
import sys
from unittest import mock
from pathlib import Path

try:
    import workflow_runtime as wf
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_runtime as wf


def check(argv, kind='command'):
    return {'argv': argv, 'kind': kind, 'rationale': 'fixture', 'covers': ['finite'], **({'selectors': ['fixture']} if kind == 'junit' else {})}


class FiniteChecksTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.logs = self.repo / 'logs'

    def script(self, source):
        return [sys.executable, '-c', source]

    def test_runs_actual_subprocess_and_returns_typed_record(self):
        records = wf.run_finite_checks(self.repo, {'checks': [check(self.script("print('ok')"))]}, self.logs)
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]['exit_code'], 0)
        self.assertEqual(records[0]['evidence']['status'], 'PASS')
        self.assertTrue(records[0]['log_sha256'])

    def test_empty_prefix_verifies_inputs_once_per_check(self):
        calls=[]
        plan={'checks':[check(self.script("print('one')")),check(self.script("print('two')"))]}
        wf.run_finite_checks(self.repo,plan,self.logs,verify_inputs=lambda: calls.append(True))
        self.assertEqual(len(calls),2)

    def test_nonzero_fails_fast(self):
        sentinel = self.repo / 'later-ran'
        plan = {'checks': [check(self.script('raise SystemExit(9)')),
                           check(self.script(f"from pathlib import Path; Path({str(sentinel)!r}).touch()"))]}
        records = wf.run_finite_checks(self.repo, plan, self.logs)
        self.assertEqual(len(records), 1)
        self.assertFalse(sentinel.exists())

    def test_verified_prefix_resumes_without_rerunning_first_check(self):
        first_count=self.repo/'first-count'; flag=self.repo/'available'; second_count=self.repo/'second-count'
        one=f"from pathlib import Path; p=Path({str(first_count)!r}); p.write_text(str(int(p.read_text())+1 if p.exists() else 1))"
        two=f"from pathlib import Path; p=Path({str(second_count)!r}); p.write_text(str(int(p.read_text())+1 if p.exists() else 1)); raise SystemExit(0 if Path({str(flag)!r}).exists() else 3)"
        plan={'checks':[check(self.script(one)),check(self.script(two))]}
        first_logs=self.repo/'frozen-logs'
        failed=wf.run_finite_checks(self.repo,plan,first_logs)
        self.assertEqual(failed[0]['evidence']['status'],'PASS')
        self.assertNotEqual(failed[1]['exit_code'],0)
        flag.touch()
        resumed=wf.run_finite_checks(self.repo,plan,self.repo/'new-logs',completed_prefix=failed[:1])
        self.assertEqual(first_count.read_text(),'1'); self.assertEqual(second_count.read_text(),'2')
        self.assertEqual([x['evidence']['status'] for x in resumed],['PASS','PASS'])
        self.assertEqual(resumed[0]['log'],failed[0]['log'])

    def test_tampered_prefix_log_rejected_before_popen(self):
        record=wf.run_finite_checks(self.repo,{'checks':[check(self.script("print('ok')"))]},self.logs)[0]
        Path(record['log']).write_text('tampered')
        with self.assertRaises(ValueError):
            wf.run_finite_checks(self.repo,{'checks':[check(self.script("print('ok')"))]},self.repo/'later',completed_prefix=[record])

    def test_completion_callback_can_externalize_typed_evidence_before_next_check(self):
        producer = "from pathlib import Path; p=Path('build/test-results/test'); p.mkdir(parents=True,exist_ok=True); (p/'TEST-fixture.xml').write_text('<testsuite tests=\"1\" failures=\"0\"/>')"
        wipe = "from pathlib import Path; import shutil; shutil.rmtree('build/test-results/test')"
        observed = []
        wf.run_finite_checks(self.repo, {'checks': [check(self.script(producer), 'junit'), check(self.script(wipe))]}, self.logs,
                             record_completed=lambda record: observed.append(json.loads(json.dumps(record))))
        self.assertEqual(observed[0]['evidence']['counts']['tests'], 1)
        self.assertTrue(observed[0]['evidence']['artifacts'])

    def test_gametest_console_and_server_log_are_independent_evidence(self):
        producer=self.repo/'fixture'/'logs'/'latest.log'; producer.parent.mkdir(parents=True)
        producer.write_text('old producer output')
        source=("from pathlib import Path; p=Path('fixture/logs/latest.log'); "
                "print('GameTest tag preflight: 2/5 fixtures selected',flush=True); "
                "p.write_text('server completion'); "
                "print('All 2 required tests passed',flush=True)")
        item=check(self.script(source),'gametest'); item['log']='fixture/logs/latest.log'
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs)[0]
        self.assertEqual(record['evidence']['status'],'PASS',record['evidence'])
        self.assertEqual(record['evidence']['selection'],'filtered')
        artifacts={Path(x['path']).resolve() for x in record['evidence']['artifacts']}
        self.assertIn(Path(record['log']).resolve(),artifacts)
        self.assertIn(producer.resolve(),artifacts)
        self.assertNotEqual(Path(record['log']).resolve(),producer.resolve())

    def test_gametest_requires_selection_completion_and_no_failure(self):
        cases=["print('All 1 required tests passed')",
               "print('GameTest tag preflight: 1/1 fixtures selected')",
               "print('GameTest tag preflight: 1/1 fixtures selected'); print('1 tests failed')"]
        for index,output in enumerate(cases):
            item=check(self.script(f"from pathlib import Path; {output}; Path('producer{index}.log').write_text('present')"),'gametest')
            item['log']=f'producer{index}.log'
            record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs/f'case{index}')[0]
            self.assertNotEqual(record['evidence']['status'],'PASS')

    def test_launch_smoke_monitors_and_preserves_independent_producer_log(self):
        producer=self.repo/'server.log'; producer.write_text('')
        source=f"from pathlib import Path; import time; Path({str(producer)!r}).write_text('SERVER READY'); time.sleep(30)"
        item=check(self.script(source),'launch-smoke'); item.update(log='server.log',ready_patterns=['SERVER READY'],error_patterns=['FATAL'])
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,smoke_timeout=2)[0]
        self.assertEqual(record['evidence']['status'],'PASS',record['evidence'])
        self.assertEqual(producer.read_text(),'SERVER READY')
        self.assertNotEqual(Path(record['log']).resolve(),producer.resolve())

    def test_typed_producer_alias_rejected_before_truncation_or_process_start(self):
        for kind in ('gametest','launch-smoke'):
            producer=self.repo/'1.log'; producer.write_text('preserve me')
            item=check(self.script("print('should not run')"),kind); item['log']='1.log'
            if kind=='launch-smoke': item.update(ready_patterns=['READY'],error_patterns=[])
            with mock.patch.object(wf.subprocess,'Popen') as popen:
                with self.assertRaisesRegex(ValueError,'aliases typed-check producer'):
                    wf.run_finite_checks(self.repo,{'checks':[item]},self.repo)
                popen.assert_not_called()
            self.assertEqual(producer.read_text(),'preserve me')

    def test_resolved_parent_alias_rejected(self):
        producer=self.repo/'1.log'; producer.write_text('preserve me')
        alias=self.repo/'alias'
        try: alias.symlink_to(self.repo,target_is_directory=True)
        except OSError: self.skipTest('directory symlink creation unavailable')
        item=check(self.script("print('should not run')"),'gametest'); item['log']='alias/1.log'
        with mock.patch.object(wf.subprocess,'Popen') as popen:
            with self.assertRaisesRegex(ValueError,'aliases typed-check producer'):
                wf.run_finite_checks(self.repo,{'checks':[item]},self.repo)
            popen.assert_not_called()
        self.assertEqual(producer.read_text(),'preserve me')

    def test_child_registration_failure_stops_owned_child_and_keeps_failure_log(self):
        seen = []
        def fail_registration(proc, _check, log):
            seen.append(proc)
            raise RuntimeError('registration failed')
        with self.assertRaisesRegex(RuntimeError, 'registration failed'):
            wf.run_finite_checks(self.repo, {'checks': [check(self.script('import time; time.sleep(30)'))]}, self.logs,
                                 child_started=fail_registration)
        self.assertTrue(seen)
        self.assertIsNotNone(seen[0].poll())
        self.assertIn('GATE COMMAND EXIT:', (self.logs / '1.log').read_text())

    def test_progress_failure_stops_owned_child(self):
        seen = []
        def fail_progress(_check, _index, _elapsed):
            seen.append(True)
            raise RuntimeError('progress failed')
        with self.assertRaisesRegex(RuntimeError, 'progress failed'):
            wf.run_finite_checks(self.repo, {'checks': [check(self.script('import time; time.sleep(30)'))]}, self.logs,
                                 progress=fail_progress)
        self.assertTrue(seen)
        self.assertIn('FINITE CHECK CALLBACK FAILED:', (self.logs / '1.log').read_text())

    @unittest.skipUnless(sys.platform == 'win32', 'Windows Gradle wrapper resolution')
    def test_windows_gradle_wrapper_prefers_batch_entrypoint_and_preserves_arguments(self):
        (self.repo / 'gradlew').write_text('#!/bin/sh\nexit 91\n')
        (self.repo / 'gradlew.bat').write_text('@echo off\necho batch-arg=%1\nexit /b 0\n')
        planned = ['./gradlew', 'arg with spaces']
        records = wf.run_finite_checks(self.repo, {'checks': [check(planned)]}, self.logs)
        self.assertEqual(records[0]['argv'], planned)
        self.assertEqual(records[0]['executed_argv'][0], str((self.repo / 'gradlew.bat').resolve()))
        self.assertEqual(records[0]['exit_code'], 0)
        self.assertIn('batch-arg="arg with spaces"', (self.logs / '1.log').read_text())


if __name__ == '__main__':
    unittest.main()
