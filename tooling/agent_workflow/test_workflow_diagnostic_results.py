import unittest
import json
import re

from workflow_result_delivery import _feedback_text


class DiagnosticResultTests(unittest.TestCase):
    def test_author_message_is_diagnostic_specific_and_acknowledgeable(self):
        payload = {
            'run_kind': 'DIAGNOSTIC', 'result': 'PASS', 'run_id': 'run_diag',
            'evaluation_generation': 1, 'event_id': 'event_diag', 'result_id': 'result_diag',
            'result_digest': 'digest', 'next_action': 'REVIEW_DIAGNOSTIC_RESULTS', 'findings': [],
        }
        text = _feedback_text(payload)
        self.assertIn('Diagnostic result: PASS', text)
        self.assertIn('only selected diagnostic checks', text)
        self.assertIn('not feature validation, manual testing, or acceptance', text)
        self.assertIn('acknowledge_result', text)
        self.assertNotIn('Workflow result:', text)

    def test_diagnostic_fail_and_error_remain_coded_check_outcomes(self):
        base = {'run_kind':'DIAGNOSTIC','run_id':'r','evaluation_generation':1,'event_id':'e',
                'result_id':'i','result_digest':'d','findings':[], 'next_action':'FIX_FINDINGS'}
        self.assertIn('Diagnostic checks failed', _feedback_text(dict(base, result='FAIL', findings=[])))
        current = _feedback_text(dict(base, result='ERROR', next_action='PREPARE_FRESH_DIAGNOSTIC'))
        self.assertIn('Diagnostic error', current)
        self.assertIn('inspect workflow status and logs', current)
        self.assertIn('fresh diagnostic scope/submission', current)
        self.assertIn('no owner approval is required', current)
        self.assertIn('does not complete or replace the feature', current)
        self.assertIn('Next action: PREPARE_FRESH_DIAGNOSTIC.', current)
        ack_text = re.search(r'arguments: (\{.*\})$', current).group(1)
        ack = json.loads(ack_text)
        self.assertEqual(set(ack), {'contract_version','command_id','event_id','run_id',
                                    'evaluation_generation','result_id','result_digest'})
        historical = _feedback_text(dict(base, result='ERROR', next_action='WAIT_FOR_RECOVERY'))
        self.assertIn('Next action: WAIT_FOR_RECOVERY.', historical)
        self.assertIn('fresh diagnostic scope/submission', historical)


if __name__ == '__main__':
    unittest.main()
