import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, IdempotencyConflict, StaleCommand, validate


class IncidentStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.store=WorkflowStore(Path(self.tmp.name)/"incidents.sqlite")
        db=self.store._connect()
        self.run={"contract_version":1,"run_id":"run_incident","sprint_id":"sprint_x","run_kind":"FEATURE_GATE","input_digest":"a"*64,"plan_digest":"b"*64,"policy_digest":"c"*64,"author":{"session_id":"author","worktree":"worktree","role":"author"},"current_attempt":1,"sequence":0}
        self.attempt={"contract_version":1,"run_id":"run_incident","generation":1,"phase":"DECIDED","result":"PASS","closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}
        validate(self.run,"run"); validate(self.attempt,"attempt")
        db.execute("INSERT INTO runs VALUES(?,?,?,?,?,0)",("run_incident","sprint_x","worktree",json.dumps(self.run),1))
        db.execute("INSERT INTO attempts VALUES(?,?,?)",("run_incident",1,json.dumps(self.attempt)))
        db.execute("INSERT INTO tasks(id,run_id,generation,logical_key,task_json,result_digest,result_json) VALUES(?,?,?,?,?,?,?)",
                   ("task_proof","run_incident",1,"proof",json.dumps({"task_id":"task_proof","kind":"CHECK","state":"COMPLETED","input_digest":"a"*64,"result":{"decision":"PASS"}}),"x","{}"))
        # Valid registered artifact scope for resolution proof.
        artifact={"artifact":{"contract_version":1,"artifact_id":"artifact_proof","run_id":"run_incident","producer_task_id":"task_proof","type":"TEST_LOG","path":"proof.log","sha256":"d"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_proof"},"input_digest":"a"*64}
        db.execute("INSERT INTO artifacts VALUES(?,?,?)",("artifact_proof","run_incident",json.dumps(artifact)))
        db.commit(); db.close()
        self.incident={"contract_version":1,"incident_id":"incident_one","run_id":"run_incident","evaluation_generation":1,"code":"FAULT","message":"fault detail","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}

    def tearDown(self):
        self.store.close(); self.tmp.cleanup()

    def test_record_reopen_replay_and_changed_content_conflict(self):
        self.assertEqual(self.store.record_incident(self.incident)["disposition"],"RECORDED")
        self.assertEqual(self.store.record_incident(self.incident)["disposition"],"REPLAYED")
        reopened=WorkflowStore(self.store.path)
        self.assertEqual(reopened.get_incident("incident_one")["incident"]["state"],"OPEN")
        reopened.close()
        with self.assertRaises(IdempotencyConflict): self.store.record_incident(dict(self.incident,message="changed"))
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM incidents").fetchone()[0],1); db.close()

    def test_seen_is_independent_idempotent_and_durable(self):
        self.store.record_incident(self.incident)
        self.store.mark_incident_seen("incident_one",now_ms=20)
        self.store.mark_incident_seen("incident_one",now_ms=30)
        got=self.store.get_incident("incident_one")
        self.assertEqual(got["seen_ms"],20); self.assertIsNone(got["resolved_ms"])
        self.assertEqual(got["incident"]["state"],"OPEN")

    def test_resolution_registered_proof_updates_state_and_replay_is_exact(self):
        self.store.record_incident(self.incident)
        first=self.store.resolve_incident("incident_one",proof_ref="artifact_proof",now_ms=40)
        self.assertEqual(first["incident"]["state"],"RESOLVED")
        self.assertEqual((first["resolved_ms"],first["resolution_proof_ref"]),(40,"artifact_proof"))
        self.assertEqual(self.store.resolve_incident("incident_one",proof_ref="artifact_proof")["resolved_ms"],40)
        with self.assertRaises(IdempotencyConflict): self.store.resolve_incident("incident_one",proof_ref="artifact_other")
        with self.assertRaises(IdempotencyConflict): self.store.resolve_incident("incident_one",proof_ref="unknown")

    def test_delivery_link_shares_state_and_preserves_saved_pass(self):
        db=self.store._connect()
        db.execute("INSERT INTO events VALUES(?,?,?,?,?,?,?)",("event_pass","run_incident",1,"RESULT","{}","0"*64,1))
        db.execute("INSERT INTO deliveries(id,event_id,recipient_json,target_kind,state,payload_digest) VALUES(?,?,?,?,?,?)",("delivery_pass","event_pass",json.dumps({"session_id":None,"worktree":"worktree","role":"owner"}),"WEBPAGE","PENDING","0"*64))
        db.commit(); db.close()
        linked=dict(self.incident,incident_id="incident_delivery")
        self.store.record_delivery_incident(incident_id="incident_delivery",delivery_id="delivery_pass",incident=linked)
        self.store.mark_incident_seen("incident_delivery",now_ms=50)
        got=self.store.get_incident("incident_delivery")
        self.assertEqual(got["delivery_id"],"delivery_pass"); self.assertEqual(got["seen_ms"],50)
        db=self.store._connect()
        self.assertEqual(json.loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id=?",("run_incident",)).fetchone()[0])["result"],"PASS")
        before=db.execute("SELECT count(*) FROM incidents").fetchone()[0]; db.close()
        with self.assertRaises((ProtocolError,StaleCommand)): self.store.record_delivery_incident(incident_id="incident_wrong",delivery_id="missing",incident=dict(linked,incident_id="incident_wrong"))
        with self.assertRaises((ProtocolError,StaleCommand)): self.store.record_delivery_incident(incident_id="incident_wrong",delivery_id="delivery_pass",incident=dict(linked,incident_id="incident_wrong",evaluation_generation=2))
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM incidents").fetchone()[0],before); db.close()
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id LIKE 'event_incident_%'").fetchone()[0],2); db.close()

    def test_seen_update_rolls_back_if_projection_wake_fails_then_retries_once(self):
        self.store.record_incident(self.incident)
        with patch.object(self.store,"_incident_page_wake_tx",side_effect=RuntimeError("injected wake failure")):
            with self.assertRaises(RuntimeError): self.store.mark_incident_seen("incident_one",now_ms=60)
        got=self.store.get_incident("incident_one")
        self.assertIsNone(got["seen_ms"])
        db=self.store._connect()
        self.assertEqual(db.execute("SELECT count(*) FROM events WHERE kind='INCIDENT_PAGE_PROJECTION' AND payload LIKE '%SEEN%' ").fetchone()[0],0)
        seen_event="event_incident_"+__import__("hashlib").sha256(b"incident_one:SEEN").hexdigest()[:32]
        self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(seen_event,)).fetchone()[0],0)
        db.close()
        self.store.mark_incident_seen("incident_one",now_ms=60)
        self.store.mark_incident_seen("incident_one",now_ms=70)
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM events WHERE id=?",(seen_event,)).fetchone()[0],1); self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(seen_event,)).fetchone()[0],1); db.close()

    def test_resolution_update_rolls_back_if_projection_wake_fails_then_retries_once(self):
        self.store.record_incident(self.incident)
        with patch.object(self.store,"_incident_page_wake_tx",side_effect=RuntimeError("injected wake failure")):
            with self.assertRaises(RuntimeError): self.store.resolve_incident("incident_one",proof_ref="artifact_proof",now_ms=80)
        got=self.store.get_incident("incident_one")
        self.assertEqual(got["incident"]["state"],"OPEN")
        self.assertIsNone(got["resolved_ms"]); self.assertIsNone(got["resolution_proof_ref"])
        db=self.store._connect()
        event_id="event_incident_"+__import__("hashlib").sha256(b"incident_one:RESOLVED").hexdigest()[:32]
        self.assertEqual(db.execute("SELECT count(*) FROM events WHERE id=?",(event_id,)).fetchone()[0],0)
        self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(event_id,)).fetchone()[0],0)
        db.close()
        self.store.resolve_incident("incident_one",proof_ref="artifact_proof",now_ms=80)
        self.store.resolve_incident("incident_one",proof_ref="artifact_proof",now_ms=90)
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM events WHERE id=?",(event_id,)).fetchone()[0],1); self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(event_id,)).fetchone()[0],1); db.close()


if __name__=="__main__": unittest.main()
