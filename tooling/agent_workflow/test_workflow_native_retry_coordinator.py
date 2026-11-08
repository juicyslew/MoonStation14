"""Automatic coordinator integration for native ERROR linked retries."""
import json
import sys
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

import workflow_coordinator
import workflow_native_execution
import workflow_native_retry
import workflow_preflight
import workflow_store
import workflow_submission
import workflow_task_artifacts
from preflight_test_support import make_preflight_case


class TestIdentity:
    server = 'http://127.0.0.1:4096'
    def __call__(self, _context): return True


class ResultSink:
    def __init__(self, store):
        self.store = store
        self.delivered = []

    def observe(self, event_id, payload):
        saved = self.store.get_event(event_id)
        return 'APPLIED' if saved and saved['payload'] == payload else 'UNKNOWN'

    def dispatch(self, event_id, payload):
        saved = self.store.get_event(event_id)
        if not saved or saved['payload'] != payload:
            return 'UNKNOWN'
        if payload.get('result_id'):
            self.delivered.append(payload)
        return 'APPLIED'


class FakeReviewTransport:
    """Strict local review transport fixture; no network or real API calls."""
    def __init__(self, worktree):
        self.worktree = str(worktree)
        self.sessions = {}
        self.coordinator = None

    def __call__(self, _server, method, path, body=None):
        if method == 'GET' and path == '/api/session/active':
            return 200, {'data': {}}
        if method == 'GET' and path.startswith('/api/session/') and path.endswith('/inbox'):
            session_id = path[len('/api/session/'):-len('/inbox')]
            return (200, {'data': []}) if session_id in self.sessions else (404, {})
        if method == 'GET' and path.startswith('/api/session/'):
            session_id = path[len('/api/session/'):]
            session = self.sessions.get(session_id)
            return (200, {'data': session}) if session else (404, {})
        if method == 'POST' and path == '/api/session':
            session_id = body['id']
            self.sessions[session_id] = {'id': session_id, 'agent': body['agent'],
                'directory': body['location']['directory'], 'metadata': dict(body['metadata'])}
            return 200, {'data': {'id': session_id}}
        if method == 'POST' and path.startswith('/api/session/') and path.endswith('/prompt'):
            session_id = path[len('/api/session/'):-len('/prompt')]
            session = self.sessions.get(session_id)
            if session is None:
                return 404, {}
            task = self.coordinator.store.get_task(body['metadata']['task_id'])
            if task['assigned_session_id'] != session_id:
                return 409, {}
            identity = {key: task[key] for key in ('task_id', 'run_id', 'evaluation_generation',
                                                     'assignment_generation', 'input_digest')}
            api = self.coordinator.review_results
            claim = {'contract_version': 1, 'command_id': 'claim-' + task['task_id'],
                     **identity, 'session_id': session_id}
            if not api.claim(claim, session_id=session_id)['accepted']:
                return 409, {}
            assignment = api.planner.build_assignment(task['task_id'])['body']['envelope']
            receipt = next(ref for ref in assignment['artifact_refs']
                           if self.coordinator.store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')
            result = {'contract_version': 1, **identity, 'decision': 'PASS',
                'findings': [{'criterion_id': 'criterion-1', 'category': 'IMPLEMENTATION',
                    'message': 'Reviewed retained and retried check evidence', 'location': 'source tree',
                    'evidence_refs': [receipt]}], 'inspected_refs': [receipt]}
            if not api.complete(result, session_id=session_id)['accepted']:
                return 409, {}
            return 200, {'data': {'id': body['id'], 'sessionID': session_id, 'type': 'user',
                'delivery': body['delivery'], 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
        raise AssertionError(f'unexpected fake transport call: {method} {path}')


def wait_for(predicate, timeout=90):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(.05)
    raise AssertionError('automatic native retry did not reach terminal PASS before deadline')


class NativeRetryCoordinatorTests(unittest.TestCase):
    def test_fake_review_transport_active_and_unknown_session_routes(self):
        transport = FakeReviewTransport(Path('.'))
        self.assertEqual((200, {'data': {}}), transport(None, 'GET', '/api/session/active'))
        self.assertEqual((404, {}), transport(None, 'GET', '/api/session/not-created'))
        self.assertEqual({}, transport.sessions)

    def test_coordinator_automatically_retries_error_and_executes_only_unfinished_suffix(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case = make_preflight_case(root, repo_name='repo', caller='retry-coordinator', key='retry-coordinator')
            producer = case.repo / 'producer.py'
            producer.write_text("""import sys
from pathlib import Path
mode=sys.argv[1]
if mode == 'prefix':
 c=Path('prefix-count.txt'); n=int(c.read_text())+1 if c.exists() else 1; c.write_text(str(n))
 fixture=Path(next(arg.split('=',1)[1] for arg in sys.argv if arg.startswith('-Pms14GameTestDir=')))
 (fixture/'prefix.marker').write_text('prefix')
 print('GameTest tag preflight: 1/1 fixtures selected')
 print('All 1 required tests passed')
else:
 fixture=Path(next(arg.split('=',1)[1] for arg in sys.argv if arg.startswith('-Pms14GameTestDir=')))
 c=Path('counter.txt'); n=int(c.read_text())+1 if c.exists() else 1; c.write_text(str(n))
 if n == 1:
  (fixture/'dirty.marker').write_text('dirty')
  print('GameTest tag preflight: 1/1 fixtures selected')
  raise SystemExit(127)
 if (fixture/'dirty.marker').exists(): raise SystemExit(2)
 print('GameTest tag preflight: 1/1 fixtures selected')
 print('All 1 required tests passed')
""", encoding='utf-8')
            (case.repo / '.gitignore').write_text('counter.txt\nprefix-count.txt\nbuild/\n', encoding='utf-8')
            case.request['input_digest'] = workflow_store.digest(workflow_preflight.capture_source(case.repo))
            checks = [
                {'argv': [sys.executable, str(producer), 'prefix',
                    '-Pms14GameTestDir=build/workflow-fixtures/'+case.request['input_digest'][:12]], 'rationale': 'Complete retained prefix',
                 'covers': ['c1'], 'kind': 'gametest', 'selectors': ['feature.prefix'],
                 'log': 'build/workflow-fixtures/'+case.request['input_digest'][:12]+'/logs/latest.log'},
                {'argv': [sys.executable, str(producer), 'gametest',
                    '-Pms14GameTestDir=build/workflow-fixtures/'+case.request['input_digest'][:12]],
                 'rationale': 'Run accepted managed GameTests in an isolated retry fixture',
                 'covers': ['c1'], 'kind': 'gametest', 'selectors': ['feature.smoke'],
                 'log': 'build/workflow-fixtures/'+case.request['input_digest'][:12]+'/logs/latest.log'},
            ]
            plan = {'registry_version': 1, 'packs': [], 'plan': {'title': 'linked retry fixture',
                'kind': 'feature', 'acceptance_criteria': ['c1'], 'fixture': 'manual.md',
                'manual_steps': ['Run the manual scenario.'], 'references': {}, 'checks': checks},
                'program_checks': []}
            case.request['plan_digest'] = workflow_store.digest(plan)
            store = workflow_store.WorkflowStore(root / 'workflow.sqlite')
            with patch.object(workflow_preflight, 'resolve_check_packs', return_value=plan):
                accepted = workflow_submission.submit_submission(store, caller=case.caller,
                    idempotency_key=case.key, request=case.request, context=case.context,
                    artifact_dir=root / 'artifacts')
            self.assertEqual(accepted['status'], 'ACCEPTED', accepted)
            db = store._connect()
            try:
                source_task_id = db.execute('SELECT id FROM tasks WHERE run_id=?', (accepted['run_id'],)).fetchone()[0]
            finally:
                db.close()
            native = workflow_native_execution.NativeExecutionBackend(store, root / 'artifacts', root / 'native-lock')
            coordinator = workflow_coordinator.ExplicitCoordinator(store,
                bindings={'author': workflow_coordinator.CoordinatorBinding('retry-coordinator', case.context)},
                artifact_dir=root / 'artifacts', handlers={'TASK_READY': native}, identity_verifier=TestIdentity(),
                reconcile_interval=.05)
            sink = ResultSink(store)
            fake_review = FakeReviewTransport(case.repo)
            try:
                reviewer = {'implementation': {'role': 'reviewer', 'worktree': str(case.repo)}}
                pipeline = workflow_coordinator.configure_review_pipeline(coordinator, reviewer,
                    {'implementation': 'workflow-reviewer-implementation'},
                    {'review-token': workflow_coordinator.ReviewerBinding('reviewer-caller',
                        str(case.repo), 'implementation')}, transport_request=fake_review)
                fake_review.coordinator = coordinator
                def fake_cleanup(task_id):
                    task = store.get_task(task_id)
                    resources = [resource for resource in store.get_task_resources(task_id)
                                 if resource['resource_key'].startswith('review-session:')]
                    if len(resources) != 1:
                        return 'UNKNOWN'
                    resource = resources[0]
                    if resource['state'] == 'RELEASED':
                        return 'APPLIED'
                    cleanup_data = workflow_store.canonical_json({'fixture': 'strict local review session inactive',
                        'task_id': task_id, 'resource_claim': resource['claim_id']}).encode()
                    proof_ref, _ = workflow_task_artifacts.register_bytes(store, task, root / 'artifacts',
                        'PROVENANCE', cleanup_data)
                    store.release_resource(resource['resource_key'], claim_id=resource['claim_id'],
                        run_id=task['run_id'], generation=task['evaluation_generation'], task_id=task_id,
                        assignment_generation=task['assignment_generation'], identity_proof=resource['identity_proof'],
                        cleanup_proof=proof_ref)
                    return 'APPLIED'
                pipeline.review_cancellation.cleanup_completed = fake_cleanup
                coordinator.handlers['RESULT_AVAILABLE'] = sink
                coordinator.handlers['OWNER_FEEDBACK'] = sink
                coordinator.start()
                def complete():
                    current = store.get_run(accepted['run_id'])
                    db = store._connect()
                    try:
                        pending = db.execute("SELECT COUNT(*) FROM outbox WHERE state NOT IN ('ACKED','ACKNOWLEDGED')").fetchone()[0]
                    finally: db.close()
                    return (current['attempt']['phase'] == 'DECIDED' and current['attempt']['result'] == 'PASS' and
                            current['run']['current_attempt'] == 2 and pending == 0 and len(sink.delivered) >= 2)
                try:
                    wait_for(complete, timeout=40)
                except AssertionError:
                    diagnostics = {'run': store.get_run(accepted['run_id']),
                        'tasks': store.list_tasks(accepted['run_id']), 'outbox': store.outbox_summary(),
                        'coordinator_error': coordinator.last_error,
                        'resources': store.get_resource_history('native_validation'),
                        'incidents': [store.get_incident(item) for item in
                            (workflow_native_retry.NativeRetry(store, root / 'artifacts')._blocked_incident_id(accepted['run_id'], 1),)]}
                    db = store._connect()
                    try:
                        diagnostics['pending_events'] = [dict(row) for row in db.execute(
                            "SELECT e.id,e.kind,o.state,e.payload FROM outbox o JOIN events e ON e.id=o.event_id WHERE o.state NOT IN ('ACKED','ACKNOWLEDGED')")]
                    finally:
                        db.close()
                    failure = (Path(__file__).resolve().parents[2] /
                        'build/agent-workflow/workflow-redesign-plan/wr12/attempts' /
                        f'native-auto-failure-{uuid.uuid4()}')
                    failure.mkdir(parents=True, exist_ok=False)
                    (failure / 'state.json').write_text(json.dumps(diagnostics, default=str, indent=2))
                    for task in diagnostics['tasks']:
                        log = root / 'artifacts' / task['task_id'] / 'worker.log'
                        if log.is_file(): (failure / (task['task_id'] + '.worker.log')).write_bytes(log.read_bytes())
                    print(f'automatic retry failure diagnostics retained at {failure}')
                    raise AssertionError(f'automatic retry failed; diagnostics preserved at {failure}; {diagnostics}')
                self.assertEqual((case.repo / 'counter.txt').read_text(), '2')
                self.assertEqual((case.repo / 'prefix-count.txt').read_text(), '1')
                accepted_fixture = case.repo / 'build' / 'workflow-fixtures' / case.request['input_digest'][:12]
                self.assertTrue((accepted_fixture / 'dirty.marker').is_file())
                original = store.get_task(source_task_id)
                self.assertEqual(original['state'], 'COMPLETED')
                self.assertEqual(original['result']['decision'], 'ERROR')
                saved_attempt = store.get_run(accepted['run_id'])
                db = store._connect()
                try:
                    old_attempt = json.loads(db.execute('SELECT attempt_json FROM attempts WHERE run_id=? AND generation=1',
                                                        (accepted['run_id'],)).fetchone()[0])
                finally:
                    db.close()
                self.assertEqual(old_attempt['phase'], 'DECIDED')
                self.assertEqual(old_attempt['result'], 'ERROR')
                new_checks = [task for task in store.list_tasks(accepted['run_id'], 2) if task['kind'] == 'CHECK']
                self.assertEqual(len(new_checks), 1)
                self.assertEqual(new_checks[0]['result']['decision'], 'PASS')
                linked_proof = store.get_recovery_metadata(accepted['run_id'], 2)
                proof = store.get_artifact(linked_proof['fresh_input_proof'])
                proof_body = workflow_store.strict_loads(Path(proof['artifact']['path']).read_text())
                self.assertEqual([0], [link['check_index'] for link in proof_body['verified_prefix']])
                runtime_fixture = proof_body['runtime_fixtures'][0]
                self.assertNotEqual(str(accepted_fixture).lower(),
                    str(case.repo / runtime_fixture['fixture_dir']).lower())
                self.assertFalse((case.repo / runtime_fixture['fixture_dir'] / 'dirty.marker').exists())
                self.assertTrue((case.repo / runtime_fixture['log']).is_file())
                self.assertEqual('-Pms14GameTestDir='+runtime_fixture['fixture_dir'],
                    runtime_fixture['argv'][-1])
                retry_links = store.list_native_check_records(new_checks[0]['task_id'])
                self.assertEqual([1], [link['check_index'] for link in retry_links])
                retry_provenance = workflow_store.strict_loads(Path(
                    store.get_artifact(retry_links[0]['artifact_ref'])['artifact']['path']).read_text())
                self.assertEqual(runtime_fixture['fixture_dir'], retry_provenance['runtime_fixture']['fixture_dir'])
                self.assertEqual(runtime_fixture['argv'], retry_provenance['runtime_fixture']['execution_argv'])
                self.assertEqual(workflow_store.digest(checks[1]), retry_provenance['check_digest'])
                self.assertEqual(len(proof_body['verified_prefix']), 1)
                self.assertIn(proof_body['verified_prefix'][0]['artifact_ref'], original['result']['artifact_refs'])
                resources = [resource for task in store.list_tasks(accepted['run_id'])
                             for resource in store.get_task_resources(task['task_id'])]
                self.assertTrue(resources)
                self.assertTrue(all(resource['state'] == 'RELEASED' for resource in resources))
                db = store._connect()
                try:
                    states = [row[0] for row in db.execute("SELECT state FROM outbox WHERE state NOT IN ('ACKED','ACKNOWLEDGED')")]
                finally:
                    db.close()
                self.assertEqual(states, [])
                self.assertGreaterEqual(len(sink.delivered), 2)  # attempt-1 ERROR and terminal PASS
            finally:
                coordinator.stop()
                native.close()
                store.close()


if __name__ == '__main__':
    unittest.main()
