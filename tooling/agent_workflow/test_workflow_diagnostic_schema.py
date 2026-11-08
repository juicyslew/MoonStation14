import copy
import unittest

from workflow_store import validate, ProtocolError
from preflight_test_support import make_preflight_case
from pathlib import Path
import tempfile


class DiagnosticSchemaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.request = make_preflight_case(Path(self.temp.name), caller='schema-test', key='schema-key').request

    def tearDown(self):
        self.temp.cleanup()

    def test_existing_owner_ready_request_remains_valid(self):
        validate(self.request, 'submission_request')

    def test_diagnostic_variant_and_rejections(self):
        request = copy.deepcopy(self.request)
        request.update(purpose='DIAGNOSTIC', implementation_complete=False,
                       criteria=[{**c, 'status': 'DIAGNOSTIC'} for c in request['criteria']],
                       manual_steps=[], check_packs=[],
                       diagnostic_rationale='Investigate the reported behavior.',
                       diagnostic_plan={'title':'Investigate behavior','kind':'diagnostic',
                           'acceptance_criteria':['criterion'],'fixture':'build.gradle',
                           'manual_steps':[],'checks':[{'argv':['python','-V'],'rationale':'Inspect environment','covers':['criterion']}]})
        validate(request, 'submission_request')
        for changes in (
            {'implementation_complete': True}, {'diagnostic_rationale': ''},
            {'criteria': [{**request['criteria'][0], 'status':'COMPLETE'}]},
        ):
            bad = copy.deepcopy(request); bad.update(changes)
            with self.assertRaises(ProtocolError): validate(bad, 'submission_request')
        owner = copy.deepcopy(self.request)
        owner['diagnostic_rationale'] = 'unexpected'
        with self.assertRaises(ProtocolError): validate(owner, 'submission_request')

    def test_terminal_result_action_is_bound_to_run_kind(self):
        event = {'contract_version':1, 'event_id':'event_diag', 'run_id':'run_diag',
                 'evaluation_generation':1, 'input_digest':'a'*64, 'result':'PASS',
                 'result_id':'event_result', 'result_digest':'b'*64, 'findings':[],
                 'artifact_refs':[], 'run_kind':'DIAGNOSTIC',
                 'next_action':'REVIEW_DIAGNOSTIC_RESULTS'}
        validate(event, 'result_available')
        invalid = dict(event, next_action='WAIT_FOR_OWNER_TEST')
        with self.assertRaises(ProtocolError): validate(invalid, 'result_available')
        feature = dict(event, run_kind='FEATURE_GATE', next_action='WAIT_FOR_OWNER_TEST')
        validate(feature, 'result_available')
        invalid_feature = dict(feature, next_action='REVIEW_DIAGNOSTIC_RESULTS')
        with self.assertRaises(ProtocolError): validate(invalid_feature, 'result_available')
        historical = dict(feature); historical.pop('run_kind')
        validate(historical, 'result_available')


if __name__ == '__main__':
    unittest.main()
