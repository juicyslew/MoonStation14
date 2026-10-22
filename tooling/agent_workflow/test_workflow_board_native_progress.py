"""Actual native resource transitions propagate through the existing board outbox."""
import asyncio
import json
import tempfile
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_preflight
import workflow_store
from test_workflow_native_execution import admit_native_fixture


class _NativeTaskHandler:
    def __init__(self, backend): self.backend=backend
    def dispatch(self, event_id, payload):
        self.backend.dispatch(event_id,payload)
        return "APPLIED"
    def observe(self, _event_id, _payload): return "ABSENT"


class NativeBoardProgressTests(unittest.TestCase):
    def test_real_worker_held_unknown_release_each_wakes_board_and_acks_hint(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); ready=root/'producer-ready'; release=root/'producer-release'
            code=("import time\nfrom pathlib import Path\n"
                  f"ready=Path({str(ready)!r}); release=Path({str(release)!r})\n"
                  "ready.write_text('ready')\n"
                  "deadline=time.monotonic()+35\n"
                  "while not release.exists() and time.monotonic()<deadline: time.sleep(.02)\n"
                  "if not release.exists(): raise SystemExit('controlled producer hold expired')\n"
                  "p=Path('build/output.zip');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'pass')\n"
                  "c=Path('counter.txt');c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n")
            case,store,receipt,task_id=admit_native_fixture(root,caller='board-progress',key='board-progress',
                delay=.01,producer_code=code)
            source_digest_before=workflow_store.digest(workflow_preflight.capture_source(case.repo))
            self.assertEqual(case.request['input_digest'],source_digest_before)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'author':object()},
                artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                handlers={'TASK_READY':_NativeTaskHandler(backend)},owner_bindings={
                    'board-secret':workflow_coordinator.OwnerBinding('board-owner',str(case.repo))})
            hint_revisions={}
            deliver=coordinator._deliver
            def observe_delivery(claim):
                if workflow_coordinator.event_kind(claim['payload'])=='BOARD_STATE_CHANGED':
                    before=coordinator._board_revision
                    deliver(claim)
                    hint_revisions[claim['event_id']]=(before,coordinator._board_revision)
                else:
                    deliver(claim)
            coordinator._deliver=observe_delivery
            stream=None; proc=None
            try:
                async def subscribe():
                    nonlocal stream
                    stream=coordinator._board_event_stream()
                    return await anext(stream)
                async def exercise():
                    nonlocal proc
                    await subscribe()
                    coordinator.start()
                    deadline=time.monotonic()+10
                    while time.monotonic()<deadline and not ready.exists(): await asyncio.sleep(.02)
                    self.assertTrue(ready.exists(),'controlled native producer did not reach its hold')
                    proc=backend.workers.get(task_id)
                    self.assertIsNotNone(proc)
                    self.assertIsNone(proc.poll(),'producer must remain active while resource is held')
                    resource=store.get_resource_history('native_validation')[-1]
                    self.assertEqual('HELD',resource['state'])
                    identity=json.loads(resource['identity_proof'])
                    replay=store.acquire_resource(resource_key='native_validation',run_id=receipt['run_id'],generation=1,
                        task_id=task_id,assignment_generation=1,identity_proof=identity)
                    self.assertEqual('REPLAYED',replay['disposition'])
                    db=store._connect()
                    try:
                        held_events=[r[0] for r in db.execute("SELECT id FROM events WHERE payload LIKE '%BOARD_STATE_CHANGED%' ORDER BY created_ms")]
                    finally: db.close()
                    self.assertTrue(held_events,'native HELD transition must enqueue a durable board hint')
                    held_revision=await asyncio.wait_for(anext(stream),4)
                    snapshot=coordinator._board_snapshot_payload(str(case.repo))
                    self.assertEqual('occupied',snapshot['system']['native_occupancy']['state'])
                    self.assertEqual('CHECKING',store.get_run(receipt['run_id'])['attempt']['phase'])
                    self.assertIsNone(proc.poll())
                    unknown_before=coordinator._board_revision
                    await asyncio.to_thread(store.mark_resource_unknown,resource['claim_id'],identity_proof=json.loads(resource['identity_proof']))
                    await asyncio.to_thread(store.mark_resource_unknown,resource['claim_id'],identity_proof=json.loads(resource['identity_proof']))
                    unknown_revision=await asyncio.wait_for(anext(stream),4)
                    snapshot=coordinator._board_snapshot_payload(str(case.repo))
                    self.assertEqual('unknown',snapshot['system']['native_occupancy']['state'])
                    self.assertGreater(coordinator._board_revision,unknown_before)
                    self.assertIn('UNKNOWN',[fault['state'] for fault in snapshot['faults']])
                    release_revision_before=coordinator._board_revision
                    release.write_text('release')
                    await asyncio.to_thread(proc.wait,15)
                    deadline=time.monotonic()+8
                    while time.monotonic()<deadline:
                        snapshot=coordinator._board_snapshot_payload(str(case.repo))
                        if snapshot['system']['native_occupancy']['state']=='free': break
                        await asyncio.sleep(.025)
                    self.assertEqual('free',snapshot['system']['native_occupancy']['state'])
                    await stream.aclose()
                    return held_revision,unknown_revision,release_revision_before
                held_revision,unknown_revision,release_revision_before=asyncio.run(exercise())
                self.assertEqual('COMPLETED',store.get_task(task_id)['state'])
                self.assertEqual('RELEASED',store.get_resource_history('native_validation')[-1]['state'])
                released=store.get_resource_history('native_validation')[-1]
                replay=store.release_resource('native_validation',claim_id=released['claim_id'],run_id=receipt['run_id'],
                    generation=1,task_id=task_id,assignment_generation=1,identity_proof=json.loads(released['identity_proof']),
                    cleanup_proof=released['cleanup_proof'])
                self.assertEqual('REPLAYED',replay['disposition'])
                self.assertEqual('1',(case.repo/'counter.txt').read_text())
                deadline=time.monotonic()+6
                hints=[]
                while time.monotonic()<deadline:
                    db=store._connect()
                    try:
                        hints=db.execute("SELECT o.state FROM outbox o JOIN events e ON e.id=o.event_id WHERE e.kind='BOARD_STATE_CHANGED' ORDER BY e.created_ms").fetchall()
                    finally: db.close()
                    if len(hints)==3 and all(row[0]=='ACKNOWLEDGED' for row in hints): break
                    time.sleep(.025)
                self.assertEqual(['ACKNOWLEDGED','ACKNOWLEDGED','ACKNOWLEDGED'],[row[0] for row in hints])
                self.assertEqual(3,len(hint_revisions))
                self.assertTrue(all(after>before for before,after in hint_revisions.values()))
                self.assertGreater(coordinator._board_revision,release_revision_before)
                self.assertTrue(held_revision.startswith(b'event: board-revision'))
                self.assertTrue(unknown_revision.startswith(b'event: board-revision'))
                self.assertIsNotNone(proc.poll())
                self.assertEqual({},backend.scopes)
                self.assertEqual('CLEANED',store.get_native_scope(task_id)['state'])
                self.assertEqual(source_digest_before,workflow_store.digest(workflow_preflight.capture_source(case.repo)))
            finally:
                release.touch(exist_ok=True)
                coordinator.stop()
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=8)


if __name__=='__main__': unittest.main()
