"""Deterministic, non-destructive managed GameTest fixture adaptation tests."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import workflow_native_fixtures
import workflow_native_worker
import workflow_check_packs
import workflow_runtime


class NativeFixturePolicyTests(unittest.TestCase):
    def test_unit_adaptation_and_fresh_producer_commands_are_distinct(self):
        old={'kind':'junit','selectors':['org.example.Test.case'],
             'argv':['./gradlew','test','--tests','org.example.Test.case','--no-daemon']}
        new=dict(old,argv=['./gradlew','test','--rerun','--tests','org.example.Test.case','--no-daemon'])
        binding=workflow_native_fixtures.managed_execution_bindings([old,new])
        self.assertEqual([0],[item['check_index'] for item in binding])
        adapted=workflow_native_fixtures.adapt_plan({'checks':[old]},binding)
        self.assertEqual(new['argv'],adapted['checks'][0]['argv'])
        self.assertEqual([],workflow_native_fixtures.managed_execution_bindings([{'kind':'junit','argv':['python','-m','unittest'],'selectors':['test_x']}]))
        with self.assertRaises(ValueError):
            workflow_native_fixtures.accepted_binding(dict(old,argv=old['argv']+['--clean']), 'a'*64,0)

    def test_unit_execution_records_real_windows_wrapper_command(self):
        old={'kind':'junit','selectors':['org.example.Test.case'],
             'argv':['./gradlew','test','--tests','org.example.Test.case','--no-daemon']}
        fresh=workflow_native_fixtures.adapt_plan({'checks':[old]},workflow_native_fixtures.managed_execution_bindings([old]))['checks'][0]
        with tempfile.TemporaryDirectory() as tmp:
            wrapper=Path(tmp)/'gradlew.bat'; wrapper.write_text('@echo off\n',encoding='utf-8')
            actual=[str(wrapper.resolve()),*fresh['argv'][1:]]
            record={'argv':fresh['argv'],'executed_argv':actual}
            provenance=workflow_native_fixtures.execution_binding(old,'a'*64,0,record,repo=tmp)
            self.assertEqual(actual,provenance['actual_execution_argv'])
            for bad in ([*actual[:-1],'--clean'],['gradlew.bat',*actual[1:]]):
                with self.assertRaises(ValueError):
                    workflow_native_fixtures.execution_binding(old,'a'*64,0,dict(record,executed_argv=bad),repo=tmp)

    def test_unit_worker_preflight_and_fresh_evidence_collection(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo=Path(tmp); stale=repo/'build/test-results/test'; stale.mkdir(parents=True)
            (repo/'manual.md').write_text('test',encoding='utf-8')
            (stale/'TEST-stale.xml').write_text('<testsuite tests="7" failures="0"/>',encoding='utf-8')
            check={'kind':'junit','selectors':['org.example.Test'], 'argv':['./gradlew','test','--rerun','--tests','org.example.Test','--no-daemon'], 'rationale':'test','covers':['unit']}
            plan={'checks':[check]}
            workflow_native_worker._preflight_fixture_bindings(plan,plan,'a'*64,repo=repo)
            script=repo/'fake-gradle.py'
            script.write_text("import pathlib,sys\np=pathlib.Path('build/test-results/test'); p.mkdir(parents=True,exist_ok=True)\nif '--rerun' in sys.argv:\n for old in p.glob('TEST-*.xml'): old.unlink()\n (p/'TEST-fresh.xml').write_text('<testsuite name=\"org.example.Test\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"org.example.Test\" name=\"works\"/></testsuite>')\n",encoding='utf-8')
            check['argv']=[sys.executable,str(script),'--rerun']
            record=workflow_runtime.run_finite_checks(repo,{'checks':[check]},repo/'logs')[0]
            self.assertEqual('PASS',record['evidence']['status'])
            self.assertEqual(1,record['evidence']['counts']['tests'])
            self.assertEqual(1,record['evidence']['counts']['tests'])
            script.write_text('import sys\n',encoding='utf-8')
            record=workflow_runtime.run_finite_checks(repo,{'checks':[dict(check,argv=[sys.executable,str(script)])]},repo/'logs2')[0]
            self.assertEqual('UNVERIFIED',record['evidence']['status'])
            self.assertEqual(0,record['evidence']['counts']['tests'])

    def test_native_preflight_requires_exact_authorized_adapted_plan(self):
        digest='a'*64
        unit={'kind':'junit','selectors':['org.example.Test'],'argv':['./gradlew','test','--tests','org.example.Test','--no-daemon'],'covers':['unit']}
        package={'kind':'build','argv':['./gradlew','clean','build','--no-daemon'],'artifacts':['build/libs/out.jar'],'covers':['package']}
        plan={'checks':[unit,package]}
        adapted=workflow_native_fixtures.adapt_plan(plan,
            workflow_native_fixtures.package_execution_bindings(plan['checks'])+
            workflow_native_fixtures.managed_execution_bindings(plan['checks']))
        workflow_native_worker._preflight_fixture_bindings(plan,adapted,digest)
        for index,argv in ((0,['./gradlew','test','--tests','org.example.Evil','--no-daemon']),
                           (1,['./gradlew','clean','build','--no-daemon'])):
            wrong=workflow_native_fixtures.copy.deepcopy(adapted)
            wrong['checks'][index]['argv']=argv
            with self.assertRaisesRegex(ValueError,'execution plan differs'):
                workflow_native_worker._preflight_fixture_bindings(plan,wrong,digest)


    def test_exact_legacy_package_command_adapts_without_clean_and_records_provenance(self):
        check = {'kind':'build','argv':['./gradlew','clean','build','--no-daemon'],
                 'artifacts':['build/libs/package.jar']}
        plan={'checks':[check]}
        binding=workflow_native_fixtures.package_execution_bindings(plan['checks'])
        adapted=workflow_native_fixtures.adapt_plan(plan,binding)
        self.assertEqual(check['argv'],plan['checks'][0]['argv'])
        self.assertEqual(['./gradlew','build','--no-daemon'],adapted['checks'][0]['argv'])
        repo=Path(tempfile.gettempdir())
        record={'argv':adapted['checks'][0]['argv'],'executed_argv':workflow_runtime.execution_argv(repo,adapted['checks'][0]['argv'])}
        evidence=workflow_native_fixtures.execution_binding(check,'a'*64,0,record,repo=repo)
        self.assertEqual('managed-package-no-clean-v1',evidence['policy_id'])
        self.assertEqual(adapted['checks'][0]['argv'],evidence['execution_argv'])
        self.assertEqual(record['executed_argv'],evidence['actual_execution_argv'])
        altered=dict(check,argv=['./gradlew','clean','build','--no-daemon','--offline'])
        self.assertEqual([],workflow_native_fixtures.package_execution_bindings([altered]))

    def test_package_filter_preserves_game_test_binding_and_original_prefix_proof(self):
        digest='a'*64
        fixture='build/workflow-fixtures/'+digest[:12]
        gametest={'kind':'gametest','argv':['./gradlew','runGameTestServer',
            '-Pms14GameTestDir='+fixture], 'log':fixture+'/logs/latest.log'}
        command={'kind':'command','argv':['python','-m','unittest']}
        package={'kind':'build','argv':['./gradlew','clean','build','--no-daemon'],
            'artifacts':['build/libs/package.jar']}
        bindings=workflow_native_fixtures.package_execution_bindings([gametest,command,package])
        self.assertEqual([2],[item['check_index'] for item in bindings])
        repo=Path(tempfile.gettempdir())
        actual=workflow_runtime.execution_argv(repo,['./gradlew','build','--no-daemon'])
        provenance=dict(bindings[0],actual_execution_argv=actual)
        record={'argv':['./gradlew','build','--no-daemon'],
                'executed_argv':actual}
        self.assertTrue(workflow_native_fixtures.verify_original_execution(
            package,digest,2,record,provenance,repo))
        for altered in (['./gradlew','clean','build','--no-daemon'],
                        ['./gradlew','build','--no-daemon','--offline']):
            bad=dict(record,executed_argv=altered)
            self.assertFalse(workflow_native_fixtures.verify_original_execution(
                package,digest,2,bad,provenance,repo))
        bad_provenance=dict(provenance,check_digest='0'*64)
        self.assertFalse(workflow_native_fixtures.verify_original_execution(
            package,digest,2,record,bad_provenance,repo))
        self.assertIsNotNone(workflow_native_fixtures.accepted_binding(gametest,digest,0))

    def test_worker_preflight_rejects_foreign_regression_fixture_before_launch(self):
        digest = 'e' * 64
        acceptance = 'accept_regression-case'
        fixture = 'build/workflow-regression/' + acceptance
        check = {'kind': 'gametest', 'argv': ['./gradlew', 'runGameTestServer',
            '-Pms14GameTestDir=' + fixture], 'log': fixture + '/logs/latest.log'}
        context = {'run_kind': 'POST_ACCEPTANCE_REGRESSION', 'run_id': 'run_regression',
            'acceptance_id': acceptance, 'input_digest': digest}
        workflow_native_worker._preflight_fixture_bindings({'checks': [check]}, {'checks': [check]},
            digest, regression_context=context)
        bad = dict(check, log='build/workflow-regression/accept_other/logs/latest.log')
        launched = []
        with self.assertRaisesRegex(ValueError, 'saved owner acceptance'):
            workflow_native_worker._preflight_fixture_bindings({'checks': [bad]}, {'checks': [bad]},
                digest, regression_context=context)
            launched.append(True)
        self.assertEqual(launched, [])

    def test_task_generation_check_bound_paths_are_unique_and_do_not_touch_owner_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            (repo / '.gitignore').write_text('build/\n', encoding='utf-8')
            (repo / 'opencode.jsonc').write_text('{"owner":"keep"}', encoding='utf-8')
            subprocess.run(['git', 'init', '-q'], cwd=repo, check=True)
            digest = 'a' * 64
            original_dir = 'build/workflow-fixtures/' + digest[:12]
            check = {'kind': 'gametest', 'argv': ['./gradlew', 'runGameTestServer',
                '-Pms14GameTestDir=' + original_dir], 'log': original_dir + '/logs/latest.log'}
            first = workflow_native_fixtures.derive_retry_bindings([check], input_digest=digest,
                run_id='run_a', generation=2, task_id='task_a', prefix_count=0)
            replay = workflow_native_fixtures.derive_retry_bindings([check], input_digest=digest,
                run_id='run_a', generation=2, task_id='task_a', prefix_count=0)
            other = workflow_native_fixtures.derive_retry_bindings([check], input_digest=digest,
                run_id='run_a', generation=3, task_id='task_b', prefix_count=0)
            self.assertEqual(first, replay)
            self.assertNotEqual(first[0]['fixture_dir'], other[0]['fixture_dir'])
            workflow_native_fixtures.reserve_retry_bindings(repo, first)
            path = repo / first[0]['fixture_dir']
            self.assertTrue(path.is_dir())
            self.assertEqual(list(path.iterdir()), [])
            self.assertEqual((repo / 'opencode.jsonc').read_text(encoding='utf-8'), '{"owner":"keep"}')
            plan = {'checks': [check]}
            adapted = workflow_native_fixtures.adapt_plan(plan, first)
            self.assertEqual(plan['checks'][0]['argv'], check['argv'])
            self.assertEqual(adapted['checks'][0]['argv'], first[0]['argv'])
            self.assertEqual(adapted['checks'][0]['log'], first[0]['log'])

    def test_initial_native_fixture_is_per_execution_and_prefix_proof_checks_exact_index(self):
        digest='a'*64
        fixture='build/workflow-fixtures/'+digest[:12]
        unit={'kind':'command','argv':['python','-m','unittest']}
        check={'kind':'gametest','argv':['./gradlew','runGameTestServer','-Pms14GameTestDir='+fixture],
               'log':fixture+'/logs/latest.log'}
        checks=[unit,check]
        a=workflow_native_fixtures.derive_initial_bindings(checks,input_digest=digest,run_id='r1',generation=1,task_id='t1')
        b=workflow_native_fixtures.derive_initial_bindings(checks,input_digest=digest,run_id='r2',generation=1,task_id='t2')
        self.assertEqual(1,a[0]['check_index']); self.assertNotEqual(a[0]['fixture_dir'],b[0]['fixture_dir'])
        adapted=workflow_native_fixtures.adapt_plan({'checks':checks},a)
        workflow_native_worker._preflight_fixture_bindings({'checks':checks},adapted,digest,retry_bindings={1:a[0]})
        actual=workflow_runtime.execution_argv('.',a[0]['argv'])
        producer=Path(a[0]['log']).resolve()
        record={'argv':a[0]['argv'],'executed_argv':actual,'source_log':str((Path.cwd()/'capture.log').resolve()),
            'evidence':{'artifacts':[{'source_path':str(producer),'path':str(producer),
                'sha256':'a'*64,'size':1,'artifact_ref':'artifact_fixture'}]}}
        provenance=workflow_native_fixtures.execution_binding(check,digest,1,record,a[0],repo='.')
        self.assertTrue(workflow_native_fixtures.verify_original_execution(check,digest,1,record,provenance,'.',run_id='r1',generation=1,task_id='t1'))
        self.assertFalse(workflow_native_fixtures.verify_original_execution(check,digest,1,record,provenance,'.',run_id='r2',generation=1,task_id='t1'))
        altered=dict(record,executed_argv=['evil.exe'])
        self.assertFalse(workflow_native_fixtures.verify_original_execution(check,digest,1,altered,provenance,'.',run_id='r1',generation=1,task_id='t1'))
        with self.assertRaisesRegex(ValueError,'executed argv or log'):
            workflow_native_fixtures.execution_binding(check,digest,1,dict(record,argv=['evil']),a[0],repo='.')
        accepted=workflow_native_fixtures.accepted_binding(check,digest,1)
        self.assertEqual('ACCEPTED_ORIGINAL',accepted['mode'])

    def test_dirty_derived_fixture_is_blocked_without_deleting_user_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            (repo / '.gitignore').write_text('build/\n', encoding='utf-8')
            subprocess.run(['git', 'init', '-q'], cwd=repo, check=True)
            digest = 'b' * 64
            fixture = 'build/workflow-fixtures/' + digest[:12]
            check = {'kind': 'gametest', 'argv': ['./gradlew', 'runGameTestServer',
                '-Pms14GameTestDir=' + fixture], 'log': fixture + '/logs/latest.log'}
            binding = workflow_native_fixtures.derive_retry_bindings([check], input_digest=digest,
                run_id='run_b', generation=2, task_id='task_b', prefix_count=0)
            path = repo / binding[0]['fixture_dir']; path.mkdir(parents=True)
            marker = path / 'owner.marker'; marker.write_text('preserve', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'fixture directory already exists|not initially empty'):
                workflow_native_fixtures.reserve_retry_bindings(repo, binding)
            self.assertEqual(marker.read_text(encoding='utf-8'), 'preserve')

    def test_unmanaged_game_test_directory_is_not_automatically_retried(self):
        digest = 'c' * 64
        check = {'kind': 'gametest', 'argv': ['./gradlew', 'runGameTestServer',
            '-Pms14GameTestDir=build/custom-owner-fixture'],
            'log': 'build/custom-owner-fixture/logs/latest.log'}
        with self.assertRaisesRegex(ValueError, 'unmanaged GameTest'):
            workflow_native_fixtures.derive_retry_bindings([check], input_digest=digest,
                run_id='run_c', generation=2, task_id='task_c', prefix_count=0)

    def test_owner_regression_fixture_requires_exact_saved_acceptance_context(self):
        digest = 'd' * 64
        acceptance = 'accept_regression-case'
        fixture = 'build/workflow-regression/' + acceptance
        check = {'kind': 'gametest', 'argv': ['./gradlew', 'runGameTestServer',
            '-Pms14GameTestDir=' + fixture, '--no-daemon'], 'log': fixture + '/logs/latest.log'}
        context = {'run_kind': 'POST_ACCEPTANCE_REGRESSION', 'run_id': 'run_regression',
            'acceptance_id': acceptance, 'input_digest': digest}
        binding = workflow_native_fixtures.accepted_binding(check, digest, 0,
            regression_context=context)
        self.assertEqual(binding['mode'], 'ACCEPTED_REGRESSION')
        self.assertEqual(binding['acceptance_id'], acceptance)
        foreign = dict(context, acceptance_id='accept_another')
        with self.assertRaisesRegex(ValueError, 'saved owner acceptance'):
            workflow_native_fixtures.accepted_binding(check, digest, 0, regression_context=foreign)
        with self.assertRaisesRegex(ValueError, 'unmanaged GameTest'):
            workflow_native_fixtures.accepted_binding(check, digest, 0)


if __name__ == '__main__':
    unittest.main()
