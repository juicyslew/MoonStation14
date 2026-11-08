"""Focused conservative projection tests for diagnostic ERROR continuation."""
import unittest

import workflow_coordinator
import workflow_store


class Store:
    def __init__(self, *, cleanup='CONFIRMED', resources=(), scopes=(), phase='DECIDED', result='ERROR'):
        self.saved = {'run': {'run_kind':'DIAGNOSTIC','current_attempt':1},
                      'attempt': {'phase':phase,'result':result,'cleanup_state':cleanup}}
        self.resources, self.scopes = resources, scopes

    def get_run(self, _): return self.saved
    def list_tasks(self, _run, _generation=None): return [{'task_id':'task-1','state':'COMPLETED'}]
    def get_task_resources(self, _): return list(self.resources)
    def list_native_scopes(self): return list(self.scopes)


class DiagnosticResubmissionProjectionTests(unittest.TestCase):
    @staticmethod
    def event(action, run_kind='DIAGNOSTIC'):
        value = {'contract_version':1,'event_id':'event_x','run_id':'run_x',
            'evaluation_generation':1,'input_digest':'a'*64,'result':'ERROR',
            'result_id':'event_x','result_digest':'b'*64,'findings':[],'artifact_refs':[],
            'next_action':action,'error_code':'NATIVE_ERROR','error_message':'failed',
            'incident_ref':'incident_x'}
        if run_kind is not None:
            value['run_kind'] = run_kind
        return value

    def test_schema_accepts_new_and_historical_diagnostic_errors_but_strict_feature(self):
        for action in ('PREPARE_FRESH_DIAGNOSTIC','WAIT_FOR_RECOVERY'):
            workflow_store.validate(self.event(action), 'result_available')
        workflow_store.validate(self.event('WAIT_FOR_RECOVERY','FEATURE_GATE'), 'result_available')
        workflow_store.validate(self.event('WAIT_FOR_RECOVERY',None), 'result_available')
        with self.assertRaises(workflow_store.ProtocolError):
            workflow_store.validate(self.event('PREPARE_FRESH_DIAGNOSTIC','FEATURE_GATE'), 'result_available')
        with self.assertRaises(workflow_store.ProtocolError):
            workflow_store.validate(self.event('PREPARE_FRESH_DIAGNOSTIC',None), 'result_available')
        missing = self.event('WAIT_FOR_RECOVERY')
        del missing['incident_ref']
        with self.assertRaises(workflow_store.ProtocolError):
            workflow_store.validate(missing, 'result_available')

    def test_settled_error_guides_fresh_diagnostic_without_mutating_event(self):
        store = Store(scopes=[{'task':{'run_id':'run-1'},'state':'CLEANED'}])
        immutable = {'next_action':'WAIT_FOR_RECOVERY','result':'ERROR'}
        self.assertEqual(workflow_coordinator._diagnostic_continuation(store,'run-1'),
                         'PREPARE_FRESH_DIAGNOSTIC')
        self.assertEqual(immutable, {'next_action':'WAIT_FOR_RECOVERY','result':'ERROR'})

    def test_unknown_resources_or_scope_cleanup_wait_conservatively(self):
        self.assertEqual(workflow_coordinator._diagnostic_continuation(
            Store(resources=[{'state':'UNKNOWN'}]),'run-1'),'WAIT_FOR_RECOVERY')
        self.assertEqual(workflow_coordinator._diagnostic_continuation(
            Store(scopes=[{'task':{'run_id':'run-1'},'state':'UNKNOWN'}]),'run-1'),'WAIT_FOR_RECOVERY')

    def test_other_diagnostic_results_retain_existing_guidance(self):
        self.assertEqual(workflow_coordinator._diagnostic_continuation(
            Store(result='FAIL'),'run-1'),'FIX_FINDINGS')
        self.assertEqual(workflow_coordinator._diagnostic_continuation(
            Store(result='PASS'),'run-1'),'REVIEW_DIAGNOSTIC_RESULTS')


if __name__ == '__main__':
    unittest.main()
