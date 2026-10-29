"""Focused checks for exact Gradle wrapper execution provenance."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_native_fixtures
import workflow_runtime


class ExecutionArgvTests(unittest.TestCase):
    def test_long_junit_selectors_use_exact_json_transport(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            script=root/'tooling'/'agent_workflow'/'validation-junit-selectors.gradle'
            script.parent.mkdir(parents=True)
            script.write_text('// managed',encoding='utf-8')
            selectors=[f'org.example.Case{i:03d}Test' for i in range(250)]
            selectors[0]='org.example.Café*'
            argv=['C:/repo/gradlew.bat','test',*sum((['--tests',item] for item in selectors),[]),'--no-daemon']
            with patch.object(workflow_runtime,'__file__',str(script)):
                actual=workflow_runtime._prepare_check_argv({'kind':'junit','selectors':selectors},argv,root/'logs'/'1.log',os_name='nt')
            self.assertLess(workflow_runtime._windows_batch_command_length(actual),workflow_runtime.WINDOWS_BATCH_ARGV_LIMIT)
            saved=__import__('json').loads((root/'logs'/'1.selectors.json').read_text(encoding='utf-8'))
            self.assertEqual(selectors,saved)
            self.assertNotIn('--tests',actual)
            self.assertTrue(any(item.startswith('-Pms14JUnitSelectors=') for item in actual))

    def test_oversized_non_junit_command_rejected_and_normal_command_unchanged(self):
        long=['C:/repo/gradlew.bat','build','x'*7000]
        with self.assertRaisesRegex(ValueError,'not launched'):
            workflow_runtime._prepare_check_argv({'kind':'build'},long,'ignored',os_name='nt')
        normal=['C:/repo/gradlew.bat','test','--tests','Focused','--no-daemon']
        self.assertEqual(normal,workflow_runtime._prepare_check_argv({'kind':'junit','selectors':['Focused']},normal,'ignored',os_name='nt'))

    def test_other_batch_and_cmd_host_are_rejected_and_unicode_uses_utf16_units(self):
        selectors=['Fixture']
        for argv in (['C:/repo/other.cmd','x'*7000],['cmd.exe','/c','x'*7000]):
            with self.assertRaisesRegex(ValueError,'batch limit'):
                workflow_runtime._prepare_check_argv({'kind':'command','selectors':selectors},argv,'ignored',os_name='nt')
        emoji='😀'*17000
        self.assertLess(len(emoji),32766)
        self.assertGreater(workflow_runtime._windows_batch_command_length(['program.exe',emoji]),32766)
        with self.assertRaisesRegex(ValueError,'CreateProcess'):
            workflow_runtime._prepare_check_argv({'kind':'command'},['program.exe',emoji],'ignored',os_name='nt')
        malformed=['C:/repo/gradlew.bat','test','--tests','Fixture','--tests']
        with self.assertRaisesRegex(ValueError,'trailing --tests'):
            workflow_runtime._prepare_check_argv({'kind':'junit','selectors':['Fixture']},malformed,'ignored',os_name='nt')

    def test_compacted_argv_launches_real_windows_batch_fixture(self):
        if workflow_runtime.os.name!='nt': self.skipTest('Windows batch fixture')
        with tempfile.TemporaryDirectory(prefix='ms14 path ') as tmp:
            root=Path(tmp)
            marker=root/'launched.txt'
            wrapper=root/'gradlew.bat'
            wrapper.write_text(f'@echo off\r\necho launched>"{marker}"\r\n',encoding='utf-8')
            selectors=[f'org.fixture.generated.combat.registry.Case{i:03d}Test' for i in range(131)]
            argv=[str(wrapper),'test',*sum((['--tests',s] for s in selectors),[]),'--no-daemon']
            self.assertGreater(workflow_runtime._windows_batch_command_length(argv),workflow_runtime.WINDOWS_BATCH_ARGV_LIMIT)
            compact=workflow_runtime._prepare_check_argv({'kind':'junit','selectors':selectors},argv,root/'logs'/'check.log')
            self.assertLess(workflow_runtime._windows_batch_command_length(compact),workflow_runtime.WINDOWS_BATCH_ARGV_LIMIT)
            proc=workflow_runtime.subprocess.Popen(compact,cwd=root,stdout=workflow_runtime.subprocess.PIPE,stderr=workflow_runtime.subprocess.STDOUT,shell=False)
            output=proc.communicate(timeout=10)[0].decode(errors='replace')
            self.assertEqual(0,proc.returncode,output)
            self.assertTrue(marker.is_file())
            self.assertEqual(selectors,__import__('json').loads((root/'logs'/'check.selectors.json').read_text(encoding='utf-8')))
    def test_game_test_logging_only_applies_to_gradle_launcher(self):
        game_test=['python','runGameTestServer']
        self.assertEqual(game_test,workflow_runtime.execution_argv('.',game_test))
        with tempfile.TemporaryDirectory() as tmp:
            repo=Path(tmp)
            (repo/'tooling'/'agent_workflow').mkdir(parents=True)
            (repo/'tooling'/'agent_workflow'/'validation-logging.gradle').write_text('// managed')
            actual=workflow_runtime.execution_argv(repo,['gradle','runGameTestServer'])
            self.assertIn('-Pms14ValidationDebug=false',actual)
            self.assertIn('--init-script',actual)

    def test_windows_launcher_resolution_is_exact_and_posix_preserves_argv(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo=Path(tmp)
            wrapper=repo/'gradlew.bat'
            wrapper.write_text('@echo off\n',encoding='utf-8')
            logical=['./gradlew','build','--no-daemon']
            actual=workflow_runtime.execution_argv(repo,logical,os_name='nt')
            self.assertEqual([str(wrapper.resolve()),'build','--no-daemon'],actual)
            self.assertEqual(logical,workflow_runtime.execution_argv(repo,logical,os_name='posix'))
            check={'kind':'build','argv':['./gradlew','clean','build','--no-daemon'],
                   'artifacts':['build/libs/package.jar']}
            binding=workflow_native_fixtures.accepted_binding(check,'a'*64,0)
            record={'argv':logical,'executed_argv':actual}
            evidence=workflow_native_fixtures.execution_binding(check,'a'*64,0,record,repo=repo)
            # This host is Windows, so the binding records the resolved path.
            if workflow_runtime.os.name=='nt':
                self.assertEqual(actual,evidence['actual_execution_argv'])
                for invalid in ([str(repo/'elsewhere'/'gradlew.bat'),'build','--no-daemon'],
                                [str(wrapper.resolve()),'clean','build','--no-daemon'],
                                ['gradle','build','--no-daemon']):
                    with self.assertRaisesRegex(ValueError,'executed argv differs'):
                        workflow_native_fixtures.execution_binding(check,'a'*64,0,
                            {'argv':logical,'executed_argv':invalid},repo=repo)
                self.assertTrue(workflow_native_fixtures.verify_original_execution(
                    check,'a'*64,0,record,dict(binding,actual_execution_argv=actual),repo))
                wrong=dict(binding,actual_execution_argv=[str(repo/'elsewhere'/'gradlew.bat'),'build','--no-daemon'])
                self.assertFalse(workflow_native_fixtures.verify_original_execution(
                    check,'a'*64,0,record,wrong,repo))


if __name__=='__main__':
    unittest.main()
