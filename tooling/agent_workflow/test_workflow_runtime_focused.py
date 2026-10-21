"""Focused checks for exact Gradle wrapper execution provenance."""
import tempfile
import unittest
from pathlib import Path

import workflow_native_fixtures
import workflow_runtime


class ExecutionArgvTests(unittest.TestCase):
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
