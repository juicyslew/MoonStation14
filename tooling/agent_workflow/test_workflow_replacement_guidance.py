import unittest
import json
import tempfile
from pathlib import Path
from types import SimpleNamespace

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_coordinator_mcp
import workflow_store


class FailedRunReplacementGuidanceTests(unittest.TestCase):
    def test_historical_rejection_is_immutable_but_guidance_explains_new_path(self):
        receipt={"status":"REJECTED","result":"ERROR","code":"SUBMISSION_REPLACEMENT_UNAVAILABLE",
                 "incident_ref":"incident_retained"}
        before=dict(receipt)
        guidance=workflow_coordinator._submission_guidance(receipt)
        self.assertEqual(receipt,before)
        self.assertEqual(guidance['next_action'],'PREPARE_FAILED_RUN_REPLACEMENT')
        self.assertEqual(guidance['field'],'replaces_run_id')
        for phrase in ('fresh scope/submission keys','fresh artifact IDs','original owned, settled FAIL run ID','Never edit or reuse'):
            self.assertIn(phrase,guidance['recovery'])
        unsettled=workflow_coordinator._submission_guidance({"status":"REJECTED","result":"ERROR","code":"FEATURE_HEAD_UNSETTLED"})
        self.assertEqual(unsettled['next_action'],'WAIT_FOR_RECOVERY')
        self.assertIn('status',unsettled['recovery'])
        self.assertIn('Do not force-cancel',unsettled['recovery'])

    def test_replacement_is_typed_request_not_new_mcp_argument(self):
        submit=workflow_coordinator_mcp.SCHEMAS['submit']
        self.assertEqual(set(submit['properties']),{'request_path','idempotency_key'})
        self.assertIn('replaces_run_id',submit['properties']['request_path']['description'])
        self.assertIn('request JSON',workflow_coordinator_mcp.DESCRIPTIONS['submit'])

    def test_http_status_and_exact_submit_replay_preserve_rejected_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            repo = root / 'repo'
            repo.mkdir()
            artifacts = root / 'artifacts'
            artifacts.mkdir()
            store = workflow_store.WorkflowStore(root / 'state.sqlite')
            fixture = json.loads((Path(__file__).parents[2] /
                'docs/development-workflow-redesign/contracts/fixtures/positive.json').read_text(encoding='utf-8'))[0]
            request = dict(fixture)
            worktree = str(repo.resolve())
            request.update(source_worktree=worktree,
                author={'session_id': 'session-safe', 'worktree': worktree, 'role': 'author'},
                idempotency_key='retained-key')
            receipt = {'contract_version': 1, 'status': 'REJECTED', 'result': 'ERROR',
                'code': 'SUBMISSION_REPLACEMENT_UNAVAILABLE', 'incident_ref': 'incident_retained',
                'next_action': 'WAIT_FOR_RECOVERY'}
            incident = {'contract_version': 1, 'incident_id': 'incident_retained',
                'code': 'SUBMISSION_REPLACEMENT_UNAVAILABLE', 'message': 'Original rejection retained.',
                'recovery_owner': 'SYSTEM', 'state': 'OPEN', 'owner_action_required': False,
                'artifact_refs': []}
            store.record_rejection(caller='caller', idempotency_key='retained-key',
                raw_request=request, response=receipt, incident=incident)
            ctx = SimpleNamespace(repo=worktree, authenticated_author=request['author'])
            coordinator = workflow_coordinator.ExplicitCoordinator(store,
                bindings={'token': workflow_coordinator.CoordinatorBinding('caller', ctx)},
                artifact_dir=artifacts, handlers={}, identity_verifier=lambda _: True)
            headers = {'Authorization': 'Bearer token'}
            def table_counts():
                conn = store._connect()
                try:
                    return {table: conn.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0]
                        for table in ('runs', 'tasks', 'events', 'submissions', 'incidents')}
                finally:
                    conn.close()
            before_counts = table_counts()
            client = TestClient(coordinator.app())
            try:
                status = client.get('/v1/status?idempotency_key=retained-key', headers=headers)
                replay = client.post('/v1/submit', headers=headers,
                    json={'idempotency_key': 'retained-key', 'request': request})
            finally:
                client.close()
            for response, receipt_field in ((status, 'submission'), (replay, 'response')):
                self.assertEqual(response.status_code, 200)
                self.assertLess(len(response.content), 4096)
                self.assertEqual(response.json()[receipt_field], receipt)
                guidance = response.json()['submission_guidance']
                self.assertEqual((guidance['code'], guidance['next_action'], guidance['field']),
                    ('SUBMISSION_REPLACEMENT_UNAVAILABLE', 'PREPARE_FAILED_RUN_REPLACEMENT', 'replaces_run_id'))
            after_counts = table_counts()
            self.assertEqual(store.get_submission('caller', 'retained-key'), receipt)
            self.assertEqual(store.get_incident('incident_retained')['incident']['state'], 'OPEN')
            self.assertEqual(before_counts, {'runs': 0, 'tasks': 0, 'events': before_counts['events'],
                'submissions': 1, 'incidents': 1})
            self.assertEqual(after_counts, before_counts)


if __name__ == '__main__':
    unittest.main()
