"""Focused transactional enqueue tests; fixtures seed a valid terminal storage row."""
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, IdempotencyConflict, StaleCommand, validate


class DeliveryEnqueueTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = WorkflowStore(Path(self.tmp.name) / "delivery.sqlite")
        self.db = self.store._connect()
        self.run = {"contract_version":1,"run_id":"run_delivery","sprint_id":"sprint_x",
                    "run_kind":"FEATURE_GATE","input_digest":"a"*64,"plan_digest":"b"*64,
                    "policy_digest":"c"*64,"author":{"session_id":"author-session","worktree":"worktree","role":"author"},
                    "current_attempt":1,"sequence":0}
        self.attempt = {"contract_version":1,"run_id":"run_delivery","generation":1,
                        "phase":"DECIDED","result":"PASS","closure":None,
                        "proposed_result":None,"cleanup_state":"NOT_REQUIRED",
                        "pause_reason":None,"proposed_closure":None}
        validate(self.run,"run"); validate(self.attempt,"attempt")
        self.db.execute("INSERT INTO runs VALUES(?,?,?,?,?,0)",("run_delivery","sprint_x","worktree",__import__("json").dumps(self.run),1))
        self.db.execute("INSERT INTO attempts VALUES(?,?,?)",("run_delivery",1,__import__("json").dumps(self.attempt)))
        self.db.commit()
        self.event = {"contract_version":1,"event_id":"event_result_delivery","run_id":"run_delivery",
                      "evaluation_generation":1,"input_digest":"a"*64,"result":"PASS",
                      "result_id":"event_result_id","result_digest":"d"*64,"findings":[],
                      "artifact_refs":[],"next_action":"WAIT_FOR_OWNER_TEST"}

    def tearDown(self):
        self.db.close(); self.store.close(); self.tmp.cleanup()

    def enqueue(self, delivery_id, target, recipient, event=None):
        return self.store.enqueue_delivery(event=event or self.event,delivery_id=delivery_id,
                                           recipient=recipient,target_kind=target)

    def test_author_and_webpage_share_one_immutable_event_and_wake(self):
        author=self.enqueue("delivery_author","AUTHOR",self.run["author"])
        page=self.enqueue("delivery_page","WEBPAGE",{"session_id":None,"worktree":"worktree","role":"owner"})
        self.assertEqual(author["event_id"],page["event_id"])
        db=self.store._connect()
        self.assertEqual(db.execute("SELECT count(*) FROM events WHERE id=?",(self.event["event_id"],)).fetchone()[0],1)
        self.assertEqual(db.execute("SELECT count(*) FROM deliveries WHERE event_id=?",(self.event["event_id"],)).fetchone()[0],2)
        self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(self.event["event_id"],)).fetchone()[0],1)
        db.close()
        self.assertEqual(self.store.get_delivery("delivery_page")["recipient"],
                         {"session_id":None,"worktree":"worktree","role":"owner"})

    def test_exact_replay_is_stable_and_changed_event_or_recipient_conflicts(self):
        first=self.enqueue("delivery_once","AUTHOR",self.run["author"])
        self.assertEqual(self.enqueue("delivery_once","AUTHOR",self.run["author"])["disposition"],"REPLAYED")
        self.enqueue("delivery_page","WEBPAGE",{"session_id":None,"worktree":"worktree","role":"owner"})
        with self.assertRaises(IdempotencyConflict):
            self.enqueue("delivery_page_alt","WEBPAGE",{"session_id":None,"worktree":"worktree","role":"owner"})
        changed=dict(self.event,result_digest="e"*64)
        with self.assertRaises(IdempotencyConflict):
            self.enqueue("delivery_other","WEBPAGE",{"session_id":None,"worktree":"worktree","role":"owner"},changed)
        self.assertEqual(self.store.get_delivery("delivery_once")["payload_digest"],first["payload_digest"])

    def test_rejects_nonterminal_and_mismatched_saved_bindings_without_mutation(self):
        self.db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=?",(__import__("json").dumps(dict(self.attempt,phase="REVIEWING")),"run_delivery")); self.db.commit()
        with self.assertRaises(StaleCommand): self.enqueue("delivery_pending","AUTHOR",self.run["author"])
        self.db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=?",(__import__("json").dumps(self.attempt),"run_delivery")); self.db.commit()
        for key,value in (("run_id","run_wrong"),("evaluation_generation",2),("input_digest","e"*64)):
            with self.subTest(key=key), self.assertRaises((StaleCommand,ProtocolError)):
                self.enqueue("delivery_bad_"+key,"AUTHOR",self.run["author"],dict(self.event,**{key:value}))
        with self.assertRaises(StaleCommand): self.enqueue("delivery_not_author","AUTHOR",dict(self.run["author"],session_id="other"))
        invalid_bindings=(dict(self.run["author"],role="reviewer"),dict(self.run["author"],worktree="other"),
                          {"session_id":"author-session","role":"author"},dict(self.run["author"],owner_id="local"))
        for index,binding in enumerate(invalid_bindings):
            with self.subTest(binding=binding),self.assertRaises((StaleCommand,ProtocolError)):
                self.enqueue("delivery_bad_binding_"+str(index),"AUTHOR",binding)
        db=self.store._connect(); self.assertEqual(db.execute("SELECT count(*) FROM deliveries").fetchone()[0],0); db.close()

    def test_transaction_rollback_leaves_no_event_delivery_or_wake(self):
        self.db.execute("BEGIN IMMEDIATE")
        self.store._enqueue_delivery_tx(self.db,event=self.event,delivery_id="delivery_rollback",
                                       recipient=self.run["author"],target_kind="AUTHOR")
        self.db.rollback()
        self.assertIsNone(self.store.get_delivery("delivery_rollback"))
        db=self.store._connect()
        self.assertEqual(db.execute("SELECT count(*) FROM events WHERE id=?",(self.event["event_id"],)).fetchone()[0],0)
        self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id=?",(self.event["event_id"],)).fetchone()[0],0)
        db.close()

    def _author_delivery(self):
        self.enqueue("delivery_ack","AUTHOR",self.run["author"])
        return self.store.claim_delivery(now_ms=100,lease_ms=10)

    def _ack(self, **changes):
        ack={"contract_version":1,"event_id":self.event["event_id"],"run_id":"run_delivery",
             "evaluation_generation":1,"result_id":self.event["result_id"],
             "result_digest":self.event["result_digest"],"author_session_id":"author-session",
             "command_id":"command_ack"}
        ack.update(changes)
        return ack

    def _transport_receipt(self, claim):
        return {"message_id":claim["message_id"],"event_id":claim["event_id"],
                "recipient":claim["recipient"],"payload_digest":claim["payload_digest"],"accepted":True}

    def test_transport_receipt_is_distinct_idempotent_api_observation(self):
        claim=self._author_delivery()
        receipt=self._transport_receipt(claim)
        self.store.record_delivery_transport(claim["delivery_id"],claim_generation=claim["claim_generation"],
                                            lease_token=claim["lease_token"],receipt=receipt)
        self.store.record_delivery_transport(claim["delivery_id"],claim_generation=claim["claim_generation"],
                                            lease_token=claim["lease_token"],receipt=receipt)
        saved=self.store.get_delivery(claim["delivery_id"])
        self.assertEqual(saved["state"],"TRANSPORT_ACCEPTED")
        self.assertIsNone(saved["protocol_ack"])
        self.assertEqual(saved["attempts"],1)

    def test_expired_lease_redelivers_same_body_and_fences_old_receipt(self):
        first=self._author_delivery()
        second=self.store.claim_delivery(now_ms=111,lease_ms=10)
        self.assertEqual((second["payload"],second["payload_digest"],second["event_id"],second["message_id"]),
                         (first["payload"],first["payload_digest"],first["event_id"],first["message_id"]))
        with self.assertRaises(StaleCommand):
            self.store.record_delivery_transport(first["delivery_id"],claim_generation=first["claim_generation"],
                                                lease_token=first["lease_token"],receipt=self._transport_receipt(first))
        self.store.record_delivery_transport(second["delivery_id"],claim_generation=second["claim_generation"],
                                            lease_token=second["lease_token"],receipt=self._transport_receipt(second))

    def test_ack_before_transport_retires_author_only_and_late_transport_is_historical(self):
        self.enqueue("delivery_ack","AUTHOR",self.run["author"])
        self.enqueue("delivery_page","WEBPAGE",{"session_id":None,"worktree":"worktree","role":"owner"})
        author=self.store.claim_delivery(now_ms=100,lease_ms=10)
        self.store.acknowledge_delivery(self._ack(),authenticated_session="author-session")
        late=self._transport_receipt(author)
        late_result=self.store.record_delivery_transport(author["delivery_id"],claim_generation=author["claim_generation"],
                                                        lease_token=author["lease_token"],receipt=late)
        self.assertEqual(late_result["disposition"],"HISTORICAL_STALE")
        saved=self.store.get_delivery("delivery_ack")
        self.assertEqual(saved["state"],"ACKNOWLEDGED")
        self.assertEqual(self.store.acknowledge_delivery(self._ack(),authenticated_session="author-session")["disposition"],"REPLAYED")
        db=self.store._connect()
        page=db.execute("SELECT state,protocol_ack FROM deliveries WHERE id='delivery_page'").fetchone()
        self.assertEqual(tuple(page),("PENDING",None))
        db.close()

    def test_wrong_ack_bindings_and_changed_duplicate_reject_without_mutation(self):
        self._author_delivery()
        invalid=(
            (self._ack(),"wrong-session"),
            (self._ack(result_digest="e"*64),"author-session"),
            (self._ack(evaluation_generation=2),"author-session"),
            (self._ack(result_id="event_wrong"),"author-session"),
        )
        for ack,session in invalid:
            with self.subTest(ack=ack,session=session),self.assertRaises((StaleCommand,ProtocolError)):
                self.store.acknowledge_delivery(ack,authenticated_session=session)
        self.store.acknowledge_delivery(self._ack(),authenticated_session="author-session")
        with self.assertRaises(IdempotencyConflict):
            self.store.acknowledge_delivery(self._ack(command_id="changed-command"),authenticated_session="author-session")
        self.assertEqual(self.store.get_delivery("delivery_ack")["state"],"ACKNOWLEDGED")

    def test_generation_one_ack_closes_only_old_delivery_after_generation_two(self):
        self.enqueue("delivery_ack","AUTHOR",self.run["author"])
        self.db.execute("UPDATE runs SET current_generation=2 WHERE id='run_delivery'")
        attempt2=dict(self.attempt,generation=2,phase="DECIDED",result="FAIL")
        self.db.execute("INSERT INTO attempts VALUES(?,?,?)",("run_delivery",2,__import__("json").dumps(attempt2)))
        self.db.commit()
        self.store.acknowledge_delivery(self._ack(),authenticated_session="author-session")
        self.assertEqual(self.store.get_delivery("delivery_ack")["state"],"ACKNOWLEDGED")
        self.assertEqual(self.store.get_run("run_delivery")["attempt"]["generation"],2)
        self.assertIsNone(self.store.claim_delivery(now_ms=1000))


if __name__ == "__main__":
    unittest.main()
