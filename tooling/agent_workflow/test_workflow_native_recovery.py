import tempfile, time, unittest
from pathlib import Path
from unittest.mock import patch

import workflow_native_execution, workflow_native_recovery, workflow_store
import workflow_coordinator
from test_workflow_native_execution import admit_native_fixture, settle_native_error_fixture


class NativeRecoveryAbsenceTests(unittest.TestCase):
    def test_coordinator_retries_startup_unknown_and_skips_new_healthy_scope(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); orphan_root=root/'orphan'; orphan_root.mkdir()
            timeline=root/'orphan-started'
            _,store,_,taskid=admit_native_fixture(orphan_root,caller='reconcile-orphan',key='reconcile-orphan',timeline=timeline,delay=30)
            orphan_backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'orphan-lock')
            orphan_proc=None; coordinator=None; final_backend=None
            try:
                orphan_backend._worker_finished=lambda _task, worker: worker.wait()
                orphan_backend.dispatch('orphan-start',{'task_id':taskid}); orphan_proc=orphan_backend.workers[taskid]
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(taskid)
                    if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                orphan_backend.scopes.pop(taskid).close()
                orphan_proc.wait(timeout=10)

                recovery=workflow_native_recovery.NativeRecovery(store,root/'artifacts')
                original_cleanup=recovery.cancellation.cleanup_scope
                running_id='task_unrelated_running'
                running_saved={'task_id':running_id,'state':'OWNED','scope_name':'native-unrelated',
                    'creation_artifact_ref':'unused','task':dict(store.get_task(taskid),task_id=running_id,state='RUNNING')}
                with patch.object(store,'list_native_scopes',return_value=[running_saved]), \
                        patch.object(store,'get_task_resources',return_value=[{'state':'HELD'}]), \
                        patch.object(recovery.cancellation,'cleanup_scope') as cleanup:
                    untouched=recovery.reconcile(unresolved_only=True)
                self.assertEqual(untouched['cleaned'],[])
                cleanup.assert_not_called()
                first=[]
                def fail_once(target):
                    if not first:
                        first.append(target)
                        raise workflow_store.StaleCommand('injected transient identity uncertainty')
                    return original_cleanup(target)
                with patch.object(recovery.cancellation,'cleanup_scope',side_effect=fail_once):
                    coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'fixture':'token'},
                        artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                        native_recovery=recovery,reconcile_interval=.05)
                    coordinator.start()
                    deadline=time.monotonic()+10
                    while time.monotonic()<deadline:
                        if (store.get_native_scope(taskid)['state']=='CLEANED' and
                                store.get_task_resources(taskid)[0]['state']=='RELEASED' and
                                store.get_task(taskid).get('result') is not None):
                            break
                        time.sleep(.025)
                self.assertEqual(first,[taskid])
                self.assertEqual(store.get_native_scope(taskid)['state'],'CLEANED')
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                recovered_task=store.get_task(taskid)
                self.assertEqual((recovered_task.get('result') or {}).get('decision'),'ERROR',recovered_task)
                for incident_id in ('incident_native_recovery_'+taskid,
                        'incident_scope_'+__import__('hashlib').sha256((taskid+'NATIVE_SCOPE_CLEANUP_UNKNOWN').encode()).hexdigest()[:20]):
                    incident=store.get_incident(incident_id)
                    if incident: self.assertIsNotNone(incident['resolved_ms'])
                self.assertFalse((coordinator.last_error or '').startswith('native recovery remains unresolved:'))
                coordinator.stop(); coordinator=None
                final_root=root/'after-cleanup'; final_root.mkdir()
                _,_,_,final_id=admit_native_fixture(final_root,caller='after-cleanup',key='after-cleanup',
                    shared_store=store,delay=.1)
                final_backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'final-lock')
                self.assertEqual(final_backend.dispatch('after-cleanup-run',{'task_id':final_id}),'PENDING')
                self.assertEqual(store.get_task(final_id)['state'],'QUEUED')
                self.assertNotIn(final_id,final_backend.workers)
                old_task=store.get_task(taskid); old_run=store.get_run(old_task['run_id'])
                self.assertEqual(old_run['attempt']['phase'],'CHECKING')
                self.assertEqual(old_task['state'],'COMPLETED')
                self.assertEqual(old_task['result']['decision'],'ERROR')
                settled=settle_native_error_fixture(store,taskid)
                self.assertEqual(settled['attempt']['result'],'ERROR')
                final_backend.dispatch('after-cleanup-run',{'task_id':final_id})
                final_backend.workers[final_id].wait(timeout=20)
                self.assertEqual(store.get_task(final_id)['result']['decision'],'PASS')
            finally:
                if coordinator is not None: coordinator.stop()
                if final_backend is not None: final_backend.close()
                orphan_backend.close()
                if orphan_proc is not None and orphan_proc.poll() is None: orphan_proc.wait(timeout=5)

    def test_last_job_handle_close_proves_absence_and_releases_held_resource(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); timeline=root/'timeline.jsonl'
            _,store,_,taskid=admit_native_fixture(root,caller='restart-absence',key='restart-absence',timeline=timeline,delay=30)
            task=store.get_task(taskid)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                # Preserve the real native assignment and Job Object; suppress only
                # the normal completion watcher cleanup to model abrupt coordinator loss.
                backend._worker_finished=lambda _task, worker: worker.wait()
                backend.dispatch('restart-ready',{'task_id':taskid}); proc=backend.workers[taskid]
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(taskid)
                    if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                self.assertTrue(timeline.exists(),'native producer actually started')
                scope=backend.scopes.pop(taskid)
                scope.close()  # actual kill-on-last-close OS behavior; no taskkill
                proc.wait(timeout=10)
                self.assertEqual(store.get_native_scope(taskid)['state'],'OWNED')
                before_events=store._connect()
                try: event_count=before_events.execute('select count(*) from events').fetchone()[0]
                finally: before_events.close()
                before_artifacts=len(store.list_artifacts(task['run_id'])) if hasattr(store,'list_artifacts') else None
                summary=workflow_native_recovery.NativeRecovery(store,root/'artifacts').reconcile()
                self.assertIn(taskid,summary['errors'])
                self.assertEqual(store.get_task(taskid)['state'],'COMPLETED')
                self.assertEqual(store.get_task(taskid)['result']['decision'],'ERROR')
                incident=store.get_task_incident(taskid)
                self.assertEqual(incident['code'],'NATIVE_WORKER_INTERRUPTED')
                ref=store.get_native_scope(taskid)['cleanup_artifact_ref']
                self.assertEqual(store.get_native_scope(taskid)['state'],'CLEANED')
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                proof=workflow_store.strict_loads(Path(store.get_artifact(ref)['artifact']['path']).read_text())
                self.assertEqual(proof['method'],'CONFIRMED_KILL_ON_CLOSE_JOB_ABSENT')
                self.assertEqual(proof['active_processes'],0)
                after_events=store._connect()
                try: count=after_events.execute('select count(*) from events').fetchone()[0]
                finally: after_events.close()
                after_artifacts=len(store.list_artifacts(task['run_id'])) if hasattr(store,'list_artifacts') else None
                second=workflow_native_recovery.NativeRecovery(store,root/'artifacts').reconcile()
                self.assertFalse(second['errors']); self.assertFalse(second['unknown'])
                again=store._connect()
                try: self.assertEqual(again.execute('select count(*) from events').fetchone()[0],count)
                finally: again.close()
                if before_artifacts is not None: self.assertEqual(after_artifacts,before_artifacts+2)
                healthy_root=root/'healthy'; healthy_root.mkdir()
                _,_,_,healthy_id=admit_native_fixture(healthy_root,caller='restart-healthy',key='restart-healthy',shared_store=store,delay=.1)
                self.assertEqual(backend.dispatch('restart-healthy-event',{'task_id':healthy_id}),'PENDING')
                self.assertEqual(store.get_task(healthy_id)['state'],'QUEUED')
                self.assertNotIn(healthy_id,backend.workers)
                old_task=store.get_task(taskid); old_run=store.get_run(old_task['run_id'])
                self.assertEqual(old_run['attempt']['phase'],'CHECKING')
                self.assertEqual(old_task['result']['decision'],'ERROR')
                settled=settle_native_error_fixture(store,taskid)
                self.assertEqual(settled['attempt']['result'],'ERROR')
                backend.dispatch('restart-healthy-event',{'task_id':healthy_id})
                healthy=backend.workers[healthy_id]; healthy.wait(timeout=20)
                self.assertEqual(store.get_task(healthy_id)['result']['decision'],'PASS')
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=5)
