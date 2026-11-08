"""End-to-end owner acceptance to coordinator-driven regression service test."""
import tempfile
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_owner
import workflow_store
from test_workflow_native_execution import admit_native_fixture


class RegressionCoordinatorServiceTests(unittest.TestCase):
    def test_owner_acceptance_runs_regression_through_coordinator_thread(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            bat = '@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            case, store, feature, feature_task = admit_native_fixture(
                root, caller='service', key='service', delay=.01,
                extra_files={'gradlew.bat': bat})
            backend = workflow_native_execution.NativeExecutionBackend(store, root/'artifacts', root/'lock')
            coordinator = None
            client = None
            try:
                backend.dispatch('feature-service', {'task_id': feature_task})
                backend.workers[feature_task].wait(timeout=20)
                seq = store.get_run(feature['run_id'])['run']['sequence']
                store.transition_attempt(feature['run_id'], 1, expected_sequence=seq, phase='STOPPING', proposed_result='PASS')
                terminal = store.transition_attempt(feature['run_id'], 1, expected_sequence=seq+1,
                    phase='DECIDED', terminal_result='PASS', cleanup_state='NOT_REQUIRED')
                result = store.get_event(terminal['event_id'])['payload']
                owner = {'session_id': None, 'worktree': str(case.repo.resolve()), 'role': 'owner'}
                decision = {
                    'contract_version': 1, 'command_id': 'accept-service', 'acceptance_id': 'accept_service',
                    'run_id': feature['run_id'], 'evaluation_generation': 1,
                    'input_digest': case.request['input_digest'], 'owner': owner, 'kind': 'LOCAL_OWNER',
                    'decision': 'ACCEPT', 'findings': [], 'expected_sequence': seq+2,
                }
                accepted = workflow_owner.OwnerService(store, snapshot_dir=root/'snapshots').decide(
                    decision, authenticated_owner=owner, displayed_result_id=result['result_id'],
                    displayed_result_digest=result['result_digest'])
                frozen_feature = store.get_run(feature['run_id'])

                # Simulate a process restart at the durable acceptance/queued-regression boundary.
                db_path = store.path
                store.close()
                store = workflow_store.WorkflowStore(db_path)
                replayed = workflow_owner.OwnerService(store, snapshot_dir=root/'snapshots').decide(
                    __import__('json').loads(workflow_store.canonical_json(decision)),
                    authenticated_owner=owner, displayed_result_id=result['result_id'],
                    displayed_result_digest=result['result_digest'])
                self.assertEqual(accepted, replayed)
                self.assertEqual(accepted['acceptance_id'], replayed['acceptance_id'])
                self.assertEqual(accepted['regression_id'], replayed['regression_id'])
                self.assertEqual(1, len(store.get_owner_decisions(feature['run_id'])))
                acceptance = store.get_acceptance(accepted['acceptance_id'])
                self.assertEqual((feature['run_id'], 1, case.request['input_digest']),
                    (acceptance['run_id'], acceptance['evaluation_generation'], acceptance['input_digest']))
                job = store.get_regression_job(accepted['regression_id'])
                self.assertEqual(accepted['acceptance_id'], job['acceptance_id'])
                self.assertEqual(case.request['input_digest'], job['source_input_digest'])
                regression_inputs = store.get_regression_inputs(accepted['regression_id'])
                self.assertEqual(feature['run_id'], regression_inputs['accepted_feature_run_id'])
                self.assertEqual(case.request['input_digest'], regression_inputs['source_input_digest'])
                self.assertEqual(accepted['acceptance_id'], regression_inputs['acceptance_id'])
                self.assertIsNone(store.get_run(job['run_id']))
                self.assertEqual([], store.list_tasks(job['run_id']))
                ready_event_id = 'event_regression_ready_'+accepted['acceptance_id'][8:]
                ready_event = store.get_event(ready_event_id)
                self.assertIsNotNone(ready_event)
                # Read-only counts verify the deferred REGRESSION_READY obligation before service startup.
                db = store._connect()
                try:
                    self.assertEqual(1, db.execute(
                        "SELECT count(*) FROM regression_jobs WHERE acceptance_id=?",
                        (accepted['acceptance_id'],)).fetchone()[0])
                    self.assertEqual(1, db.execute("SELECT count(*) FROM outbox WHERE event_id=?",
                        (ready_event_id,)).fetchone()[0])
                finally:
                    db.close()

                class Identity:
                    server = 'http://127.0.0.1:9'
                    def __call__(self, context): return True

                coordinator = workflow_coordinator.ExplicitCoordinator(store,
                    bindings={'author-test': object()}, artifact_dir=root/'coordinator-artifacts',
                    handlers={'TASK_READY': backend}, identity_verifier=Identity(), reconcile_interval=.05)
                class ProjectionOnly:
                    def observe(self, event_id, payload): return 'APPLIED' if event_id in seen else 'ABSENT'
                    def dispatch(self, event_id, payload): seen.add(event_id); return 'APPLIED'
                seen = set()
                coordinator.handlers['OWNER_FEEDBACK'] = ProjectionOnly()
                workflow_coordinator.configure_review_pipeline(coordinator, {}, {}, {},
                    transport_request=lambda *a, **k: (_ for _ in ()).throw(AssertionError('review API called')))
                from starlette.testclient import TestClient
                client = TestClient(coordinator.app())
                client.__enter__()
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    job = store.get_regression_job(accepted['regression_id'])
                    if job['status'] == 'DECIDED': break
                    time.sleep(.025)
                self.assertEqual(('DECIDED', 'PASS'), (job['status'], job['result']))
                regression = store.get_run(job['run_id'])
                self.assertEqual('POST_ACCEPTANCE_REGRESSION', regression['run']['run_kind'])
                self.assertEqual(accepted['acceptance_id'], regression['run']['owner_acceptance_id'])
                self.assertEqual(case.request['input_digest'], regression['run']['input_digest'])
                self.assertEqual([job['task_id']], [item['task_id'] for item in store.list_tasks(job['run_id'])])
                self.assertEqual(frozen_feature, store.get_run(feature['run_id']))
                self.assertEqual(1, len(store.get_owner_decisions(feature['run_id'])))
                run = store.get_run(job['run_id'])
                task = store.get_task(job['task_id'])
                self.assertEqual('COMPLETED', task['state'])
                self.assertEqual('PASS', task['result']['decision'])
                self.assertEqual('DECIDED', run['attempt']['phase'])
                receipts = [store.get_artifact(ref)['artifact'] for ref in task['result']['artifact_refs']]
                receipt = next(item for item in receipts if item['type'] == 'CHECK_RECEIPT')
                import json
                checks = json.loads(Path(receipt['path']).read_text())['checks']
                self.assertEqual(1, len(checks))
                evidence = checks[0]['evidence']
                self.assertEqual('PASS', evidence['status'])
                self.assertEqual({'selected': 3, 'total': 3, 'passed': 3},
                    {key: evidence['counts'][key] for key in ('selected', 'total', 'passed')})
                self.assertEqual('1', (case.repo/'counter.txt').read_text())
                self.assertTrue(any(Path(item['path']).exists() for item in receipts))
                ready = store.get_event('event_regression_ready_'+accepted['acceptance_id'][8:])['payload']
                self.assertEqual('APPLIED', coordinator.handlers['REGRESSION_READY'].observe(ready['event_id'], ready))
                self.assertEqual('APPLIED', coordinator.handlers['REGRESSION_READY'].dispatch(ready['event_id'], ready))
                self.assertEqual(job['run_id'], store.get_regression_job(accepted['regression_id'])['run_id'])
                self.assertEqual(frozen_feature, store.get_run(feature['run_id']))
                db = store._connect()
                try:
                    self.assertEqual(1, db.execute("SELECT count(*) FROM regression_jobs WHERE acceptance_id=?",
                        (accepted['acceptance_id'],)).fetchone()[0])
                    self.assertEqual(1, db.execute("SELECT count(*) FROM runs WHERE json_extract(run_json,'$.owner_acceptance_id')=?",
                        (accepted['acceptance_id'],)).fetchone()[0])
                    self.assertEqual(1, db.execute("SELECT count(*) FROM tasks WHERE run_id=?", (job['run_id'],)).fetchone()[0])
                    task_event_id='event_regression_task_'+__import__('hashlib').sha256(accepted['regression_id'].encode()).hexdigest()[:24]
                    self.assertEqual(1, db.execute("SELECT count(*) FROM outbox WHERE event_id=?", (task_event_id,)).fetchone()[0])
                finally:
                    db.close()
            finally:
                if client is not None: client.__exit__(None, None, None)
                if coordinator is not None: coordinator.stop()
                backend.close()
