import tempfile
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_native_recovery
import workflow_store
from test_workflow_native_execution import admit_native_fixture, settle_native_error_fixture


class StartupRecoveryTests(unittest.TestCase):
    def test_startup_reconciles_owned_scope_before_dispatch_and_allows_next_task(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); timeline=root/'timeline.jsonl'
            _,store,_,taskid=admit_native_fixture(root,caller='startup-old',key='startup-old',timeline=timeline,delay=30)
            task=store.get_task(taskid)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            backend._worker_finished=lambda _task, worker: worker.wait()
            backend.dispatch('startup-old-event',{'task_id':taskid})
            old_worker=backend.workers[taskid]
            deadline=time.monotonic()+10
            while time.monotonic()<deadline:
                resources=store.get_task_resources(taskid)
                if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                time.sleep(.025)
            self.assertTrue(resources and resources[0]['state']=='HELD')
            backend.scopes.pop(taskid).close()
            old_worker.wait(timeout=10)

            class OrderedBackend:
                def dispatch(self, event_id, payload):
                    self.assert_clean()
                    self_outer.assertEqual(backend.dispatch(event_id,payload),'PENDING')
                    self_outer.assertEqual(store.get_task(healthy_id)['state'],'QUEUED')
                    self_outer.assertNotIn(healthy_id,backend.workers)
                    old_task=store.get_task(taskid); old_run=store.get_run(old_task['run_id'])
                    self_outer.assertEqual(old_run['attempt']['phase'],'CHECKING')
                    self_outer.assertEqual(old_task['state'],'COMPLETED')
                    self_outer.assertEqual(old_task['result']['decision'],'ERROR')
                    settled=settle_native_error_fixture(store,taskid)
                    self_outer.assertEqual(settled['attempt']['result'],'ERROR')
                    return backend.dispatch(event_id,payload)
                def observe(self, event_id, payload): return backend.observe(event_id,payload)
                def close(self): pass
                def assert_clean(self):
                    self_outer.assertEqual(store.get_task(taskid)['result']['decision'],'ERROR')
                    self_outer.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
            self_outer=self
            healthy_root=root/'healthy'; healthy_root.mkdir()
            _,_,_,healthy_id=admit_native_fixture(healthy_root,caller='startup-new',key='startup-new',shared_store=store,delay=.1)
            adapter=OrderedBackend()
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'token':object()},artifact_dir=root/'artifacts',handlers={'TASK_READY':adapter},identity_verifier=lambda _:True,reconcile_interval=.05,native_recovery=workflow_native_recovery.NativeRecovery(store,root/'artifacts'))
            try:
                coordinator.start()
                self.assertIn(taskid,coordinator.native_recovery_summary['errors'])
                deadline=time.monotonic()+10
                while time.monotonic()<deadline and store.get_task(healthy_id)['state']!='COMPLETED': time.sleep(.025)
                self.assertEqual(store.get_task(healthy_id)['result']['decision'],'PASS')
                self.assertEqual(store.get_task(taskid)['result']['decision'],'ERROR')
            finally:
                coordinator.stop()
                backend.close()

    def test_terminal_owned_scope_is_listed_for_recovery(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            _,store,_,taskid=admit_native_fixture(root,caller='terminal-scope',key='terminal-scope')
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            try:
                backend.dispatch('terminal-scope-ready',{'task_id':taskid})
                backend.workers[taskid].wait(timeout=20)
                self.assertEqual(store.get_task(taskid)['state'],'COMPLETED')
                # Model the crash boundary after completion, before monitor cleanup.
                db=store._connect()
                try: db.execute("UPDATE native_scopes SET state='OWNED' WHERE task_id=?",(taskid,))
                finally: db.close()
                self.assertIn(taskid,[row['task_id'] for row in store.list_native_scopes()])
            finally: backend.close()


if __name__=='__main__': unittest.main()
