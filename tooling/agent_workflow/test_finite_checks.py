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

    @unittest.skipUnless(wf.os.name == 'nt', 'Windows batch launch limit')
    def test_oversized_junit_is_not_launched_and_stale_xml_is_not_evidence(self):
        xml=self.repo/'build'/'test-results'/'test'/'TEST-Stale.xml'
        xml.parent.mkdir(parents=True)
        xml.write_text('<testsuite tests="99" failures="0"/>',encoding='utf-8')
        selectors=[f'org.example.Case{i:03d}Test' for i in range(400)]
        argv=['C:/fixture/gradlew.bat','test',*sum((['--tests',s] for s in selectors),[]),'--no-daemon']
        item=dict(check(argv,'junit'),selectors=['not.the.frozen.argv.selectors'])
        with mock.patch.object(wf.subprocess,'Popen') as popen:
            record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs)[0]
        popen.assert_not_called()
        self.assertEqual('FAIL',record['evidence']['status'])
        self.assertIsNone(record['evidence']['counts']['tests'])
        self.assertEqual([],record['evidence']['artifacts'])
        self.assertTrue(any('not launched' in finding for finding in record['evidence']['findings']))

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

    def test_gametest_completed_hanging_process_is_stopped_after_teardown_grace(self):
        import time
        output = "print('GameTest tag preflight: 2/5 fixtures selected',flush=True); print('2 GAME TESTS COMPLETE IN 0.2 s',flush=True); print('All 2 required tests passed',flush=True); import time; time.sleep(30)"
        item=check(self.script(output),'gametest'); item['log']='producer.log'
        started=time.monotonic()
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,timeout=8,gametest_teardown_timeout=.25)[0]
        self.assertLess(time.monotonic()-started,5)
        self.assertEqual(record['exit_code'],124)
        self.assertEqual(record['timeout_phase'],'gametest_teardown')
        self.assertEqual(record['tree_cleanup'],'terminated')
        self.assertNotEqual(record['evidence']['status'],'PASS')
        self.assertIn('GAME_TEST_TEARDOWN_TIMEOUT',(Path(record['log']).read_text()))

    def test_native_gametest_banner_after_large_flushed_output_starts_bounded_teardown(self):
        import time
        banner='[16:53:59] [Server thread/INFO] [minecraft/GameTestServer]: ========= 19 GAME TESTS COMPLETE IN 12.23 s ======================'
        source=("import sys,time; "
                "print('GameTest tag preflight: 19/19 fixtures selected',flush=True); "
                "sys.stdout.write('x'*140000+'\\n'); sys.stdout.flush(); time.sleep(.12); "
                f"sys.stdout.write({banner[:43]!r}); sys.stdout.flush(); time.sleep(.04); "
                f"sys.stdout.write({banner[43:]!r}+'\\n'); sys.stdout.flush(); time.sleep(30)")
        item=check(self.script(source),'gametest'); item['log']='producer.log'
        started=time.monotonic()
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,timeout=8,gametest_teardown_timeout=.2)[0]
        elapsed=time.monotonic()-started
        log=Path(record['log']).read_text()
        self.assertLess(elapsed,4)
        self.assertEqual(record['exit_code'],124)
        self.assertEqual(record['timeout_phase'],'gametest_teardown')
        self.assertEqual(record['tree_cleanup'],'terminated')
        self.assertNotEqual(record['evidence']['status'],'PASS')
        self.assertIn(banner,log)
        self.assertIn('GAME_TEST_TEARDOWN_TIMEOUT',log)

    def test_teardown_timeout_with_blocked_tree_cleanup_is_unverified_and_notified(self):
        import time
        child=[]; notifications=[]; completions=[]
        source=("import time; print('GameTest tag preflight: 1/1 fixtures selected',flush=True); "
                "print('1 GAME TESTS COMPLETE IN 0.1 s',flush=True); time.sleep(30)")
        item=check(self.script(source),'gametest'); item['log']='producer.log'
        original_stop=wf.stop_owned_tree
        def blocked_once(proc,timeout=5):
            if not child:
                child.append(proc)
                raise wf.GateTreeBlocked(proc.pid,'injected stop failure')
            return original_stop(proc,timeout=timeout)
        try:
            with mock.patch.object(wf,'stop_owned_tree',side_effect=blocked_once):
                records=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,timeout=8,
                    gametest_teardown_timeout=.2,
                    cleanup_blocked=lambda *args: notifications.append(args),
                    record_completed=lambda record: completions.append(record))
        finally:
            if child and child[0].poll() is None:
                original_stop(child[0])
        self.assertEqual(1,len(records))
        record=records[0]
        self.assertEqual(124,record['exit_code'])
        self.assertEqual('gametest_teardown',record['timeout_phase'])
        self.assertEqual('unverified',record['tree_cleanup'])
        self.assertNotEqual('PASS',record['evidence']['status'])
        self.assertIn('OWNED PROCESS TREE TERMINATION UNVERIFIED',Path(record['log']).read_text())
        self.assertEqual([record],completions)
        self.assertEqual(1,len(notifications))
        self.assertIsInstance(notifications[0][0],wf.GateTreeBlocked)
        self.assertEqual(record,notifications[0][2])
        self.assertIsNotNone(child[0].poll(), 'fixture child was not reaped after original owner stop')

    def test_global_deadline_wins_before_gametest_teardown_deadline(self):
        source=("import sys,time; print('GameTest tag preflight: 1/1 fixtures selected',flush=True); "
                "sys.stdout.write('[16:53:59] [Server thread/INFO] [minecraft/GameTestServer]: ========= 1 GAME TESTS COMPLETE IN 1.00 s ======================\\n'); "
                "sys.stdout.flush(); time.sleep(30)")
        item=check(self.script(source),'gametest'); item['log']='producer.log'
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,timeout=.3,gametest_teardown_timeout=3)[0]
        self.assertEqual(record['exit_code'],124)
        self.assertEqual(record['timeout_phase'],'command')
        self.assertEqual(record['tree_cleanup'],'terminated')
        self.assertIn('TIMEOUT:',Path(record['log']).read_text())

    def test_gametest_failed_completion_hang_retains_failure_evidence(self):
        output = "print('GameTest tag preflight: 2/5 fixtures selected',flush=True); print('2 GAME TESTS COMPLETE IN 0.2 s',flush=True); print('1 tests failed',flush=True); import time; time.sleep(30)"
        item=check(self.script(output),'gametest'); item['log']='producer.log'
        record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs,timeout=8,gametest_teardown_timeout=.25)[0]
        self.assertEqual(record['timeout_phase'],'gametest_teardown')
        self.assertEqual(record['exit_code'],124)
        self.assertNotEqual(record['evidence']['status'],'PASS')
        self.assertEqual(record['evidence']['counts']['failed'],1)
        self.assertIn('native GameTest failure summary present',record['evidence']['findings'])

    def test_gametest_incomplete_or_invalid_completion_does_not_start_teardown(self):
        import time
        cases=("print('All 2 required tests passed'); print('2 GAME TESTS COMPLETE IN 0.2 s')",
               "print('GameTest tag preflight: 2/5 fixtures selected'); print('3 GAME TESTS COMPLETE IN 0.2 s')",
               "print('GameTest tag preflight: 0/0 fixtures selected'); print('2 GAME TESTS COMPLETE IN 0.2 s')")
        for output in cases:
            item=check(self.script(output+"; import time; time.sleep(.35)"),'gametest'); item['log']='producer.log'
            started=time.monotonic()
            record=wf.run_finite_checks(self.repo,{'checks':[item]},self.logs/str(len(list(self.logs.glob('*.log')))),timeout=5,gametest_teardown_timeout=.1)[0]
            self.assertEqual(record['exit_code'],0)
            self.assertIsNone(record['timeout_phase'])
            self.assertLess(time.monotonic()-started,4)

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
