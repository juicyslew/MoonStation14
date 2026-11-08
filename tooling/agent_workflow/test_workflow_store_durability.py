"""Repository-level crash/replay coverage for the SQLite admission/outbox boundary."""
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, IdempotencyConflict, StaleCommand
from workflow_store_test_support import admission_records


class DurabilityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "workflow.sqlite"
        self.req,self.run,self.task,self.event = admission_records("durable", idempotency_key="durable-key")
        self.store = WorkflowStore(self.path)

    def tearDown(self): self.tmp.cleanup()
    def admit(self, store=None, req=None):
        return (store or self.store).submit(caller="durable-caller",request=req or self.req,run=self.run,first_task=self.task,outbox_event=self.event,logical_task_key="initial")

    def counts(self):
        db=sqlite3.connect(self.path)
        try: return {t:db.execute(f"select count(*) from {t}").fetchone()[0] for t in ("submissions","runs","attempts","tasks","events","outbox")}
        finally: db.close()

    def child(self, script, *args):
        env=dict(os.environ); env["PYTHONPATH"]=str(Path(__file__).parent)
        return subprocess.run([sys.executable,"-c",script,str(self.path),*args],env=env,capture_output=True,text=True)

    def test_crash_before_commit_rolls_back_real_admission(self):
        marker=Path(self.tmp.name)/"reached"
        script='''import os,sqlite3,sys\nfrom workflow_store import WorkflowStore\np=sys.argv[1]; marker=sys.argv[2]; s=WorkflowStore(p)\norig=sqlite3.connect\ndef connect(*a,**k):\n c=orig(*a,**k); commit=c.commit\n def crash():\n  open(marker,"w").write("before commit")\n  os._exit(71)\n c.commit=crash\n return c\nsqlite3.connect=connect\nimport json\nfrom pathlib import Path\nf=Path(__file__).parents[2]/"docs/development-workflow-redesign/contracts/fixtures/positive.json"\nr=json.loads(f.read_text())[0]\n'''
        # Child imports module by absolute checkout path; payload is passed as JSON to avoid fixture-path assumptions.
        payload=json.dumps([self.req,self.run,self.task,self.event])
        script='''import os,sqlite3,sys,json\nfrom workflow_store import WorkflowStore\np,marker,payload=sys.argv[1:]; s=WorkflowStore(p); r,run,task,event=json.loads(payload)\norig=sqlite3.connect\nclass CrashingConnection(sqlite3.Connection):\n def commit(self): open(marker,"w").write("before commit"); os._exit(71)\ndef connect(*a,**k): k["factory"]=CrashingConnection; return orig(*a,**k)\nsqlite3.connect=connect\ns.submit(caller="durable-caller",request=r,run=run,first_task=task,outbox_event=event,logical_task_key="initial")\n'''
        result=self.child(script,str(marker),payload)
        self.assertEqual(result.returncode,71); self.assertTrue(marker.exists()); self.assertEqual(self.counts(),dict.fromkeys(("submissions","runs","attempts","tasks","events","outbox"),0))

    def test_commit_then_lost_reply_replays_exact_receipt(self):
        marker=Path(self.tmp.name)/"committed"
        script='''import os,sys,json\nfrom workflow_store import WorkflowStore\np,m,payload=sys.argv[1:]; r,run,task,event=json.loads(payload); s=WorkflowStore(p); receipt=s.submit(caller="durable-caller",request=r,run=run,first_task=task,outbox_event=event,logical_task_key="initial"); open(m,"w").write(json.dumps(receipt)); os._exit(72)\n'''
        res=self.child(script,str(marker),json.dumps([self.req,self.run,self.task,self.event]))
        self.assertEqual(res.returncode,72); receipt=json.loads(marker.read_text()); self.assertEqual(self.admit(),receipt)
        self.assertEqual(self.counts(),dict.fromkeys(("submissions","runs","attempts","tasks","events","outbox"),1))

    def test_concurrent_exact_admission_is_singleton(self):
        second=WorkflowStore(self.path); barrier=threading.Barrier(2); results=[]; errors=[]
        def go(store):
            try: barrier.wait(); results.append(self.admit(store))
            except BaseException as e: errors.append(e)
        a=threading.Thread(target=go,args=(self.store,)); b=threading.Thread(target=go,args=(second,)); a.start(); b.start(); a.join(); b.join()
        self.assertEqual(errors,[]); self.assertEqual(results[0],results[1]); self.assertEqual(self.counts(),dict.fromkeys(("submissions","runs","attempts","tasks","events","outbox"),1))

    def test_changed_body_conflict_and_rejection_caller_namespaces(self):
        receipt=self.admit(); changed=dict(self.req,feature_label="changed body")
        with self.assertRaises(IdempotencyConflict): self.admit(req=changed)
        self.assertEqual(self.store.get_submission("durable-caller","durable-key"),receipt)
        response={"contract_version":1,"status":"REJECTED","result":"FAIL","code":"INVALID","findings":[{"criterion_id":"input","category":"IMPLEMENTATION","message":"Malformed input","location":"request","evidence_refs":["request"],"required_change":"Correct the request fields."}],"next_action":"FIX_FINDINGS"}
        raw={"idempotency_key":"bad","unknown":True}
        for caller in ("one","two"): self.store.record_rejection(caller=caller,idempotency_key="bad",raw_request=json.dumps(raw),response=response)
        self.assertEqual(self.counts()["submissions"],3)
        reopened=WorkflowStore(self.path); self.assertEqual(reopened.submit(caller="durable-caller",request=self.req,run=self.run,first_task=self.task,outbox_event=self.event,logical_task_key="initial"),receipt)

    def test_expired_outbox_claim_fences_stale_ack_after_reopen(self):
        self.admit(); old=self.store.claim_outbox(now_ms=1000,lease_ms=10); self.store.close(); reopened=WorkflowStore(self.path)
        new=reopened.claim_outbox(now_ms=1010,lease_ms=10)
        self.assertEqual(old["event_id"],new["event_id"]); self.assertEqual(old["payload"],new["payload"]); self.assertGreater(new["claim_generation"],old["claim_generation"])
        with self.assertRaises(StaleCommand): reopened.acknowledge_outbox(old["event_id"],old["claim_generation"])
        reopened.acknowledge_outbox(new["event_id"],new["claim_generation"])

if __name__ == "__main__": unittest.main()
