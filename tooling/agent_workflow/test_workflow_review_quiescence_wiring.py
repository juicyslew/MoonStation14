import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_review_quiescence
import workflow_supervisor
import test_workflow_review_quiescence as review_quiescence_fixtures
import test_workflow_supervisor as supervisor_fixtures
from preflight_test_support import make_preflight_case


class ReviewQuiescenceWiringTests(unittest.TestCase):
    def test_reconcile_uses_real_run_ids_and_only_admitted_active_review(self):
        fixture = review_quiescence_fixtures.ReviewQuiescenceTests()
        tmp, store, task, _owner, _api = fixture.fixture()
        self.addCleanup(tmp.cleanup)

        calls = []
        class Cancellation:
            def quiesce(self, task_id):
                calls.append(task_id)
                return 'PENDING'

        quiescence = workflow_review_quiescence.ReviewQuiescence(store, Cancellation())
        actual_ids = store.list_runs_by_phase(('REVIEWING', 'STOPPING'))
        self.assertTrue(actual_ids)
        self.assertTrue(all(isinstance(run_id, str) for run_id in actual_ids))
        result = quiescence.reconcile()
        self.assertEqual([task['task_id']], result['pending'])
        self.assertEqual([task['task_id']], calls)
        calls.clear()
        result = quiescence.reconcile(unresolved_only=True)
        self.assertEqual([task['task_id']], calls)

        class Healthy:
            def quiesce(self, task_id):
                raise AssertionError('healthy task must not be quiesced')
        healthy = workflow_review_quiescence.ReviewQuiescence(store, Healthy())
        healthy._unresolved.add(task['task_id'])
        # An APPLIED result removes a task from the retry set.
        class Applied:
            def quiesce(self, task_id): return 'APPLIED'
        applied = workflow_review_quiescence.ReviewQuiescence(store, Applied())
        self.assertEqual([task['task_id']], applied.reconcile()['confirmed'])
        self.assertEqual([], applied.reconcile(unresolved_only=True)['confirmed'])

    def test_coordinator_reconciles_under_guard_before_thread_and_on_stop(self):
        import tempfile
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        db = Path(tmp.name) / 'state.db'
        store = type('Store', (), {'path': str(db)})()
        preflight = make_preflight_case(Path(tmp.name) / 'preflight', caller='test', key='key')
        binding = workflow_coordinator.CoordinatorBinding('test', preflight.context)
        events = []
        class Hook:
            def reconcile(self, **kwargs):
                events.append((kwargs, c._thread is None, c._lock is not None))
                if not kwargs: return {}
        c = workflow_coordinator.ExplicitCoordinator(store, bindings={'test-token': binding}, artifact_dir=Path(tmp.name),
            review_quiescence=Hook(), identity_verifier=lambda context: True, reconcile_interval=.02)
        def run_once():
            events.append(('thread',))
            c.review_quiescence.reconcile(unresolved_only=True)
            c._stop.wait(.2)
        c._run = run_once
        c.start()
        self.assertEqual(({}, True, True), events[0])
        c._thread.join(1)
        c.stop()
        self.assertIn({'unresolved_only': True}, [event[0] for event in events if isinstance(event, tuple)])
        self.assertEqual({}, [event[0] for event in events if isinstance(event, tuple)][-1])

    def test_supervisor_reconciles_after_owned_child_exit_and_exhaustion(self):
        fixture = supervisor_fixtures.SupervisorTests(); fixture.setUp()
        try:
            events = []
            class Exited:
                pid = 123
                def poll(self): return 9
            def launch(*args, **kwargs): return Exited()
            obj = workflow_supervisor.WorkflowSupervisor(fixture.db, fixture.config,
                fixture.root/'health.json', command=['fake'], popen=launch,
                sleep=lambda _: None, backoff=(0, 0, 0), loss_reconciler=lambda: events.append(time.monotonic()))
            obj.run()
            self.assertEqual(4, len(events))
            self.assertTrue(all(0 <= time.monotonic()-stamp < 10 for stamp in events))
        finally:
            fixture.tearDown()


if __name__ == '__main__': unittest.main()
