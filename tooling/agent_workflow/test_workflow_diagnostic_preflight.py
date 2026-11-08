import tempfile
import unittest
from pathlib import Path

import workflow_preflight as preflight
from diagnostic_test_support import make_diagnostic_case


class DiagnosticPreflightTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.case = make_diagnostic_case(Path(self.tmp.name), caller='diagnostic', key='diag')
        self.request = self.case.request

    def tearDown(self):
        self.tmp.cleanup()

    def test_diagnostic_preflight_binds_plan_and_skips_review(self):
        bundle = preflight.preflight(self.request, caller=self.case.caller, context=self.case.context)
        self.assertEqual(bundle['review_inputs'], [])
        self.assertEqual(bundle['resolved_plan']['packs'], [])
        self.assertEqual(bundle['resolved_plan']['program_checks'], ['managed_config_provenance'])
        self.assertEqual(bundle['plan_digest'], self.request['plan_digest'])

    def test_stale_digest_rationale_and_unsafe_globs_rejected(self):
        import copy
        stale = copy.deepcopy(self.request); stale['plan_digest'] = '0'*64
        with self.assertRaises(preflight.PreflightFailure): preflight.preflight(stale, caller=self.case.caller, context=self.case.context)
        bad = copy.deepcopy(self.request); bad['diagnostic_rationale'] = ' '
        with self.assertRaises(preflight.PreflightFailure): preflight.preflight(bad, caller=self.case.caller, context=self.case.context)
        unsafe = copy.deepcopy(self.request); unsafe['diagnostic_plan']['checks'][0]['artifact_globs'] = ['../escape']
        from workflow_check_packs import resolve_diagnostic_plan
        with self.assertRaises(Exception): resolve_diagnostic_plan(unsafe, repo=self.case.repo, policy=self.case.policy)
        unsafe['plan_digest'] = '0'*64
        with self.assertRaises(preflight.PreflightFailure) as caught:
            preflight.preflight(unsafe, caller=self.case.caller, context=self.case.context)
        self.assertEqual(caught.exception.code, 'DIAGNOSTIC_CHECK_PLAN')
        self.assertNotIn('-Pms14GameTestDir=', caught.exception.required_change)

        bad_criteria = copy.deepcopy(self.request)
        bad_criteria['diagnostic_plan']['acceptance_criteria'] = ['not-trusted']
        bad_criteria['plan_digest'] = '0'*64
        with self.assertRaises(preflight.PreflightFailure) as caught:
            preflight.preflight(bad_criteria, caller=self.case.caller, context=self.case.context)
        self.assertEqual(caught.exception.code, 'DIAGNOSTIC_CHECK_PLAN')
        self.assertNotIn('-Pms14GameTestDir=', caught.exception.required_change)

    def test_unmanaged_gametest_fixture_is_correctable_preflight_failure(self):
        import copy
        from workflow_store import digest
        from workflow_check_packs import resolve_diagnostic_plan
        bad = copy.deepcopy(self.request)
        check = {'argv':['./gradlew','runGameTestServer','-Pms14GameTestDir=build/gametest-tags-fixture'],
                 'rationale':'Investigate GameTest behavior.', 'covers':['c1'], 'kind':'gametest',
                 'log':'build/gametest-tags-fixture/logs/latest.log'}
        bad['diagnostic_plan']['checks'] = [check]
        with self.assertRaises(Exception):
            resolve_diagnostic_plan(bad, repo=self.case.repo, policy=self.case.policy)
        bad['plan_digest'] = digest({'invalid': True})
        with self.assertRaises(preflight.PreflightFailure) as caught:
            preflight.preflight(bad, caller=self.case.caller, context=self.case.context)
        self.assertEqual(caught.exception.verdict, 'FAIL')
        self.assertEqual(caught.exception.code, 'DIAGNOSTIC_CHECK_PLAN')
        self.assertEqual(caught.exception.field, 'diagnostic_plan.checks.0')


if __name__ == '__main__':
    unittest.main()
