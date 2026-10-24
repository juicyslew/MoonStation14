import unittest
from unittest import mock
import ast
import hashlib
import tempfile
from pathlib import Path

import workflow_preflight
from preflight_test_support import make_preflight_case
import workflow_scope
import workflow_review_results
import workflow_store
import test_workflow_review_results as review_fixtures


class PreflightRecoveryGuidanceTests(unittest.TestCase):
    def test_every_literal_error_code_has_explicit_policy(self):
        tree=ast.parse(Path(workflow_preflight.__file__).read_text(encoding='utf-8'))
        codes=set()
        for node in ast.walk(tree):
            if (isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=='_error'
                    and node.args and isinstance(node.args[0],ast.Constant)):
                codes.add(node.args[0].value)
        # Inputs requiring operator/tooling repair intentionally remain ERROR;
        # they are explicit exceptions rather than unknown-code fallback.
        system_codes={'POLICY_SCHEMA','POLICY_BINDING','RECEIPT_HASH','RECEIPT_CORRUPT',
                      'CONFIG_RECEIPT_MISSING','CONFIG_RECEIPT_CORRUPT','CONFIG_OVERRIDE_AMBIGUOUS',
                      'RECEIPT_DRIFT','BUNDLE_CONTRACT','PREFLIGHT_ERROR'}
        self.assertEqual(codes-set(workflow_preflight._CORRECTABLE_CODES),system_codes)
        self.assertEqual(codes, set(workflow_preflight._CORRECTABLE_CODES)|system_codes)
        self.assertTrue(all(workflow_preflight._CORRECTABLE_GUIDANCE[c] for c in workflow_preflight._CORRECTABLE_CODES))

    def test_correctable_policy_codes_fail_with_recovery_guidance(self):
        codes=workflow_preflight._CORRECTABLE_CODES
        for code in codes:
            with self.subTest(code=code), self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight._error(code, 'field', 'bad')
            failure = caught.exception
            self.assertEqual(failure.verdict, 'FAIL')
            self.assertTrue(failure.required_change)
            if code == 'CONFIG_CHANGED':
                self.assertIn('Do not revert unrelated owner configuration', failure.required_change)
                self.assertIn('trusted provenance', failure.required_change)

    def test_guidance_table_has_no_duplicate_literal_keys(self):
        tree=ast.parse(Path(workflow_preflight.__file__).read_text(encoding='utf-8'))
        assignment=next(node for node in tree.body if isinstance(node,ast.Assign) and
                        any(isinstance(target,ast.Name) and target.id=='_CORRECTABLE_GUIDANCE' for target in node.targets))
        keys=[key.value for key in assignment.value.keys if isinstance(key,ast.Constant)]
        self.assertEqual(len(keys),len(set(keys)))

    def test_actual_preflight_source_and_artifact_failures_are_actionable(self):
        with tempfile.TemporaryDirectory() as root:
            case=make_preflight_case(Path(root),caller='caller',key='key')
            context=workflow_preflight.PreflightContext(case.repo,case.author,case.policy,[case.artifact],
                case.receipt,hashlib.sha256(case.receipt.read_bytes()).hexdigest())
            stale=dict(case.request,input_digest='0'*64)
            with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight.preflight(stale,caller=case.caller,context=context)
            self.assertEqual((caught.exception.code,caught.exception.verdict),('SOURCE_STALE','FAIL'))
            self.assertIn('fresh scope',caught.exception.required_change)
            missing=dict(case.artifact,path=str(Path(root)/'missing-proof'))
            context=workflow_preflight.PreflightContext(case.repo,case.author,case.policy,[missing],case.receipt,
                hashlib.sha256(case.receipt.read_bytes()).hexdigest())
            with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight.preflight(case.request,caller=case.caller,context=context)
            self.assertEqual((caught.exception.code,caught.exception.verdict),('ARTIFACT_MISSING','FAIL'))

    def test_untrusted_request_and_plan_values_are_not_echoed(self):
        with tempfile.TemporaryDirectory() as root:
            case=make_preflight_case(Path(root),caller='caller',key='key')
            context=workflow_preflight.PreflightContext(case.repo,case.author,case.policy,[case.artifact],case.receipt,
                hashlib.sha256(case.receipt.read_bytes()).hexdigest())
            secret='PRIVATE_INVALID_REQUEST_SENTINEL'
            malformed=dict(case.request,manual_steps=secret)
            with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight.preflight(malformed,caller=case.caller,context=context)
            self.assertEqual(caught.exception.code,'REQUEST_SCHEMA')
            self.assertNotIn(secret,caught.exception.message+ caught.exception.required_change)
            bad_plan=dict(case.request)
            bad_plan['check_packs']=[dict(bad_plan['check_packs'][0],selectors=[secret]),*bad_plan['check_packs'][1:]]
            with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight.preflight(bad_plan,caller=case.caller,context=context)
            self.assertEqual(caught.exception.code,'CHECK_PACK_PLAN')
            self.assertNotIn(secret,caught.exception.message+ caught.exception.required_change)

            bad_artifact=dict(case.artifact,type=secret)
            context_bad=workflow_preflight.PreflightContext(case.repo,case.author,case.policy,[bad_artifact],case.receipt,
                hashlib.sha256(case.receipt.read_bytes()).hexdigest())
            with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                workflow_preflight.preflight(case.request,caller=case.caller,context=context_bad)
            self.assertEqual(caught.exception.code,'ARTIFACT_SCHEMA')
            self.assertNotIn(secret,caught.exception.message+caught.exception.required_change)

    def test_unexpected_preflight_system_error_is_bounded_and_retry_safe(self):
        with tempfile.TemporaryDirectory() as root:
            case=make_preflight_case(Path(root),caller='caller',key='key')
            context=workflow_preflight.PreflightContext(case.repo,case.author,case.policy,[case.artifact],case.receipt,
                hashlib.sha256(case.receipt.read_bytes()).hexdigest())
            with mock.patch.object(workflow_preflight,'capture_source',side_effect=RuntimeError('PRIVATE_SYSTEM_SENTINEL')):
                with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
                    workflow_preflight.preflight(case.request,caller=case.caller,context=context)
            self.assertEqual((caught.exception.code,caught.exception.verdict),('PREFLIGHT_ERROR','ERROR'))
            self.assertNotIn('PRIVATE_SYSTEM_SENTINEL',caught.exception.message+caught.exception.required_change)
            self.assertIn('retain this submission key',caught.exception.required_change)

    def test_unknown_failure_remains_system_error_and_preserves_retry_key(self):
        with self.assertRaises(workflow_preflight.PreflightFailure) as caught:
            workflow_preflight._error('UNRECOGNIZED_STORE_FAILURE', 'store', 'unavailable')
        self.assertEqual(caught.exception.verdict, 'ERROR')
        self.assertIn('retain this submission key', caught.exception.required_change)


class ScopeRecoveryGuidanceTests(unittest.TestCase):
    def test_real_floor_violation_has_fresh_keys_and_floor_guidance(self):
        fixture=__import__('test_workflow_scope').AuthorScopeTests()
        fixture.setUp()
        try:
            payload=fixture.payload('recover-feature','recover-submit')
            baseline=dict(fixture.context.policy)
            baseline['review_roles']=['implementation','parity']
            baseline['review_summaries']={**baseline['review_summaries'],'parity':'trusted floor'}
            from dataclasses import replace
            with self.assertRaises(workflow_scope.ScopeError) as caught:
                workflow_scope.register_author_scope(fixture.db,replace(fixture.context,policy=baseline),
                    'scope-caller',payload,'registration-key',artifact_dir=fixture.archive)
            error=caught.exception
            self.assertEqual((error.code,error.field),('FLOOR_REVIEW_ROLES','policy.review_roles'))
            self.assertIn('fresh',error.recovery)
            self.assertIn('do not weaken floors',error.recovery)
        finally:
            fixture.tearDown()

    def test_invalid_policy_and_artifact_schema_errors_are_bounded(self):
        fixture=__import__('test_workflow_scope').AuthorScopeTests()
        fixture.setUp()
        try:
            sentinel='PRIVATE_SCOPE_SCHEMA_SENTINEL'
            payload=fixture.payload('bad-policy','scope-submit-policy')
            payload['policy']['review_roles']=sentinel*300
            with self.assertRaises(workflow_scope.ScopeError) as caught:
                workflow_scope.register_author_scope(fixture.db,fixture.context,'scope-caller',payload,'scope-policy-registration',artifact_dir=fixture.archive)
            policy_error=caught.exception
            self.assertEqual(policy_error.code,'POLICY_SCHEMA')
            self.assertIn('policy.review_roles',policy_error.field)
            self.assertNotIn(sentinel,str(policy_error))
            self.assertLess(len(str(policy_error)),4096)
            self.assertIn('fresh registration key',policy_error.recovery)

            payload=fixture.payload('bad-artifact','scope-submit-artifact')
            payload['artifacts'][0]['size_bytes']=sentinel*300
            with self.assertRaises(workflow_scope.ScopeError) as caught:
                workflow_scope.register_author_scope(fixture.db,fixture.context,'scope-caller',payload,'scope-artifact-registration',artifact_dir=fixture.archive)
            artifact_error=caught.exception
            self.assertEqual(artifact_error.code,'ARTIFACT_SCHEMA')
            self.assertIn('artifacts.0.size_bytes',artifact_error.field)
            self.assertNotIn(sentinel,str(artifact_error))
            self.assertLess(len(str(artifact_error)),4096)
            self.assertIn('fresh submission key',artifact_error.recovery)
        finally:
            fixture.tearDown()


class ReviewCallbackRecoveryTests(unittest.TestCase):
    def setUp(self): self.fixture=review_fixtures.ReviewResultTests()

    def _admitted(self, root):
        self.fixture.setUp()
        store,run,task,api,receipt=self.fixture.admitted(root)
        return store,task,api,receipt

    def test_all_callback_operations_classify_store_faults(self):
        failures=((workflow_store.StaleCommand('stale'),'STALE_REVIEW_TASK','REFRESH_TASK_STATUS'),
                  (workflow_store.IdempotencyConflict('conflict'),'REVIEW_RESULT_CONFLICT','CHECK_TASK_STATUS'),
                  (workflow_store.ProtocolError('bad state'),'REVIEW_TASK_STATE','REFRESH_TASK_STATUS'),
                  (workflow_store.StoreError('temporary outage'),'REVIEW_SYSTEM_ERROR','WAIT_FOR_RECOVERY'))
        with tempfile.TemporaryDirectory() as tmp:
            store,task,api,receipt=self._admitted(Path(tmp))
            good=api._validate
            claim={'contract_version':1,'command_id':'recovery-claim',
                   **{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                   'session_id':task['assigned_session_id']}
            report={'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                    'error_code':'REVIEW_TEST_FAULT','message':'synthetic test error','artifact_refs':[receipt]}
            try:
                for method, payload in (("claim", claim),
                                         ("complete", self.fixture.result(task,receipt)),
                                         ("error", report)):
                    for exception,code,action in failures:
                        with self.subTest(method=method,code=code):
                            api._validate=lambda payload,schema: None
                            target={'claim':'claim_task','complete':'complete_task','error':'report_task_error'}[method]
                            original=getattr(store,target)
                            setattr(store,target,lambda *a, _exc=exception, **k: (_ for _ in ()).throw(_exc))
                            try:
                                response=getattr(api,method)(payload,session_id=task['assigned_session_id'])
                            finally: setattr(store,target,original)
                            self.assertEqual((response['code'],response['next_action']),(code,action))
            finally:
                api._validate=good

    def test_validation_uses_safe_store_field_and_recovery_without_echo(self):
        fixture=review_fixtures.ReviewResultTests()
        fixture.setUp()
        self.addCleanup(fixture.tearDown)
        with tempfile.TemporaryDirectory() as tmp:
            _,_,task,real_api,receipt=fixture.admitted(Path(tmp))
            payload=fixture.result(task,receipt)
            payload['findings'][0]['message']={'secret':'SECRET_PAYLOAD_TEXT'}
            response=real_api._validate(payload,'review_result')
        self.assertEqual(response['field'],'findings.0.message')
        self.assertIn('required string type',response['recovery'])
        self.assertNotIn('SECRET_PAYLOAD_TEXT',str(response))


if __name__ == '__main__':
    unittest.main()
