import hashlib, json, tempfile, time, unittest
from pathlib import Path

import workflow_native_cancellation, workflow_native_execution
from test_workflow_native_execution import admit_native_fixture, settle_native_error_fixture
import workflow_store
from unittest.mock import patch


class NativeCancellationTests(unittest.TestCase):
    def test_repeated_backend_cleanup_reuses_worker_committed_proof_after_stale_owned_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,receipt,taskid=admit_native_fixture(root,caller='cleanup-replay',key='cleanup-replay',delay=.05)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            try:
                backend.dispatch('cleanup-replay-event',{'task_id':taskid})
                backend.workers[taskid].wait(timeout=20)
                cancel=workflow_native_cancellation.NativeCancellation(store,root/'artifacts')
                cancel.cleanup_scope(taskid)
                original=store.get_native_scope(taskid)
                self.assertEqual(original['state'],'CLEANED')
                original_ref=original['cleanup_artifact_ref']
                real_get=store.get_native_scope
                stale_once=[]
                def stale_first_read(target):
                    current=real_get(target)
                    if target==taskid and not stale_once:
                        stale_once.append(True)
                        return dict(current,state='OWNED',cleanup_artifact_ref=None)
                    return current
                with patch.object(store,'get_native_scope',side_effect=stale_first_read):
                    replay_ref=cancel.cleanup_scope(taskid)
                self.assertEqual(stale_once,[True])
                self.assertEqual(replay_ref,original_ref)
                self.assertEqual(real_get(taskid)['cleanup_artifact_ref'],original_ref)
                proof=store.get_artifact(original_ref)['artifact']
                raw=Path(proof['path']).read_bytes()
                self.assertEqual(hashlib.sha256(raw).hexdigest(),proof['sha256'])
                self.assertEqual(json.loads(raw)['active_processes'],0)
                self.assertEqual(store.get_task(taskid)['result']['decision'],'PASS')
            finally:
                backend.close()

    def test_unregistered_grandchild_marks_error_then_scope_cleanup_unblocks_next_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'shared.sqlite')
            marker=root/'grandchild.pid'
            code=("import subprocess,sys,time\nfrom pathlib import Path\n"
                  "p=Path('build/output.zip');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'pass')\n"
                  "c=Path('counter.txt');c.write_text('1')\n"
                  "child=subprocess.Popen([sys.executable,'-c',%r])\n" % ("import os,time;from pathlib import Path;Path(%r).write_text(str(os.getpid()));time.sleep(60)" % str(marker))+
                  "")
            firstroot=root/'first'; firstroot.mkdir()
            case,_,receipt,taskid=admit_native_fixture(firstroot,caller='leak-first',key='leak-first',shared_store=store,producer_code=code)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None; second=None
            try:
                backend.dispatch('leak-event',{'task_id':taskid}); proc=backend.workers[taskid]
                deadline=time.monotonic()+8
                while time.monotonic()<deadline and not marker.exists(): time.sleep(.025)
                self.assertTrue(marker.exists(),'grandchild actually started')
                pid=int(marker.read_text()); self.assertIsNotNone(__import__('gate_execution').process_identity(pid),'grandchild is live before worker scope drain')
                proc.wait(timeout=20)
                deadline=time.monotonic()+10
                while time.monotonic()<deadline and store.get_task(taskid)['state']!='COMPLETED': time.sleep(.025)
                first=store.get_task(taskid)
                self.assertEqual(first['state'],'COMPLETED'); self.assertEqual(first['result']['decision'],'ERROR')
                reports=[json.loads(Path(store.get_artifact(ref)['artifact']['path']).read_bytes()) for ref in first['result']['artifact_refs'] if store.get_artifact(ref)['artifact']['type']=='PROVENANCE']
                self.assertTrue(any(row.get('code')=='CLEANUP_UNKNOWN' for row in reports))
                deadline=time.monotonic()+10
                while time.monotonic()<deadline and store.get_task_resources(taskid)[0]['state']!='RELEASED': time.sleep(.025)
                self.assertEqual(store.get_native_scope(taskid)['state'],'CLEANED')
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                self.assertIsNone(__import__('gate_execution').process_identity(pid),'registered Job Object cleanup killed grandchild')
                self.assertEqual(store.get_task(taskid)['result']['decision'],'ERROR')
                self.assertFalse(any(store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT' for ref in first['result']['artifact_refs']))
                secondroot=root/'second'; secondroot.mkdir()
                _,_,receipt2,task2=admit_native_fixture(secondroot,caller='leak-second',key='leak-second',shared_store=store,delay=.1)
                self.assertEqual(backend.dispatch('healthy-event',{'task_id':task2}),'PENDING')
                self.assertEqual(store.get_task(task2)['state'],'QUEUED')
                self.assertNotIn(task2,backend.workers)
                self.assertEqual(store.get_run(first['run_id'])['attempt']['phase'],'CHECKING')
                settled=settle_native_error_fixture(store,taskid)
                self.assertEqual(settled['attempt']['result'],'ERROR')
                self.assertIsNone(backend.dispatch('healthy-event',{'task_id':task2}))
                second=backend.workers[task2]; second.wait(timeout=20)
                self.assertEqual(store.get_task(task2)['result']['decision'],'PASS')
                self.assertEqual((secondroot/'repo'/'counter.txt').read_text(),'1')
            finally:
                backend.close()
                if proc is not None: proc.wait(timeout=5)
                if second is not None: second.wait(timeout=5)

    def test_owned_native_scope_stops_before_resource_and_task_cancellation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); timeline=root/'timeline.jsonl'
            case,store,receipt,taskid=admit_native_fixture(root,caller='cancel',key='cancel',timeline=timeline,delay=30)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                backend.dispatch('native-ready',{'task_id':taskid}); proc=backend.workers[taskid]
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(taskid)
                    if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                self.assertTrue(timeline.exists(),'actual check producer started')
                task=store.get_task(taskid); run=store.get_run(task['run_id'])
                incident={'contract_version':1,'incident_id':'incident_cancel_fixture','run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'code':'TEST_STOP','message':'bounded cancellation fixture','recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
                store.transition_attempt(task['run_id'],task['evaluation_generation'],expected_sequence=run['run']['sequence'],phase='STOPPING',closure='CANCELLED')
                db=store._connect()
                try: eventid=db.execute("select id from events where kind='CANCEL_TASK' and json_extract(payload,'$.task_id')=?",(taskid,)).fetchone()[0]
                finally: db.close()
                payload=store.get_event(eventid)['payload']; cancel=workflow_native_cancellation.NativeCancellation(store,root/'artifacts')
                self.assertEqual(cancel.dispatch(eventid,payload),'APPLIED')
                self.assertEqual(store.get_task(taskid)['state'],'CANCELLED'); self.assertIsNone(store.get_task(taskid)['result'])
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                scope=store.get_native_scope(taskid); self.assertEqual(scope['state'],'CLEANED')
                artifact=store.get_artifact(scope['cleanup_artifact_ref'])['artifact']; data=Path(artifact['path']).read_bytes()
                self.assertEqual(len(data),artifact['size_bytes']); self.assertEqual(hashlib.sha256(data).hexdigest(),artifact['sha256'])
                self.assertEqual(json.loads(data)['active_processes'],0)
                count=store._connect()
                try: before=count.execute('select count(*) from artifacts').fetchone()[0]
                finally: count.close()
                self.assertEqual(cancel.observe(eventid,payload),'APPLIED'); self.assertEqual(cancel.dispatch(eventid,payload),'APPLIED')
                count=store._connect()
                try: self.assertEqual(count.execute('select count(*) from artifacts').fetchone()[0],before)
                finally: count.close()
                with self.assertRaises(workflow_store.StaleCommand):
                    store.complete_native_task(task_id=taskid,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],process_identity=store.get_native_claim(taskid)['process_identity'],claim_token=store.get_native_claim(taskid)['claim_token'],outcome={'decision':'PASS','findings':[],'artifact_refs':[]})
                current=store.get_run(task['run_id']); store.transition_attempt(task['run_id'],task['evaluation_generation'],expected_sequence=current['run']['sequence'],phase='CLOSED',cleanup_state='CONFIRMED')
                self.assertEqual(store.get_run(task['run_id'])['attempt']['closure'],'CANCELLED')
            finally:
                backend.close()
                if proc is not None: proc.wait(timeout=5)

    def test_backend_close_cleans_cancel_requested_scope_without_confirming_task(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); timeline=root/'timeline.jsonl'
            case,store,receipt,taskid=admit_native_fixture(root,caller='shutdown-cancel',key='shutdown-cancel',timeline=timeline,delay=30)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                backend.dispatch('shutdown-native-ready',{'task_id':taskid}); proc=backend.workers[taskid]
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(taskid)
                    if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                self.assertTrue(timeline.exists(),'actual check producer started')
                task=store.get_task(taskid); run=store.get_run(task['run_id'])
                store.transition_attempt(task['run_id'],task['evaluation_generation'],expected_sequence=run['run']['sequence'],phase='STOPPING',closure='CANCELLED')
                eventid=store._connect()
                try: cancelid=eventid.execute("select id from events where kind='CANCEL_TASK' and json_extract(payload,'$.task_id')=?",(taskid,)).fetchone()[0]
                finally: eventid.close()
                saved=store.get_event(cancelid); cancel=workflow_native_cancellation.NativeCancellation(store,root/'artifacts')
                backend.close()
                self.assertIsNotNone(proc.poll(),'backend close reaped owned worker')
                self.assertEqual(store.get_task(taskid)['state'],'CANCEL_REQUESTED')
                cleaned=store.get_native_scope(taskid); self.assertEqual(cleaned['state'],'CLEANED')
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                self.assertEqual(cancel.dispatch(cancelid,saved['payload']),'APPLIED')
                self.assertEqual(store.get_task(taskid)['state'],'CANCELLED')
                before=cleaned['cleanup_artifact_ref']
                backend.close()
                self.assertEqual(store.get_native_scope(taskid)['cleanup_artifact_ref'],before)
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=5)


if __name__=='__main__': unittest.main()
