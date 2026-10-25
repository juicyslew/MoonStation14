"""Focused contract and transaction checks for author-requested failed-head replacement."""
import tempfile
import threading
import unittest
from pathlib import Path

import workflow_store


class FailedRunReplacementTests(unittest.TestCase):
    def test_real_http_preflight_scope_and_submission_replaces_settled_failure(self):
        import copy
        import hashlib
        import json
        from starlette.testclient import TestClient
        import workflow_coordinator
        import workflow_preflight
        import workflow_scope
        from preflight_test_support import make_preflight_case

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case = make_preflight_case(root, caller="author", key="initial")
            store = workflow_store.WorkflowStore(root / "workflow.sqlite")
            binding = workflow_coordinator.CoordinatorBinding(case.caller, case.context)
            coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={"token": binding},
                artifact_dir=root / "artifacts", handlers={}, identity_verifier=lambda _context: True)
            client = TestClient(coordinator.app())  # no lifespan: no scheduler/provider/native dispatch
            headers = {"Authorization": "Bearer token"}

            original_scope = {"contract_version":1,"policy":case.policy,"artifacts":[case.artifact],
                "submission_idempotency_key":"initial"}
            registered = client.post("/v1/scope/register", headers=headers,
                json={"idempotency_key":"scope-initial","scope":original_scope})
            self.assertEqual(200, registered.status_code, registered.text)
            self.assertEqual("REGISTERED", registered.json()["response"]["status"])
            first_response = client.post("/v1/submit", headers=headers,
                json={"idempotency_key":"initial","request":case.request})
            self.assertEqual(200, first_response.status_code, first_response.text)
            first_receipt = first_response.json()["response"]
            self.assertEqual("ACCEPTED", first_receipt["status"])
            old_run = first_receipt["run_id"]

            # Settle only the isolated fixture's first attempt as a valid terminal FAIL.
            db = store._connect()
            try:
                attempt = {"contract_version":1,"run_id":old_run,"generation":1,"phase":"DECIDED","result":"FAIL",
                    "closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}
                db.execute("UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1", (workflow_store.canonical_json(attempt),old_run))
                task_row = db.execute("SELECT id,task_json FROM tasks WHERE run_id=?", (old_run,)).fetchone()
                old_task = workflow_store.strict_loads(task_row[1])
                old_task["state"] = "COMPLETED"
                old_task["result"] = {"decision":"FAIL","findings":[],"artifact_refs":[]}
                db.execute("UPDATE tasks SET task_json=? WHERE id=?", (workflow_store.canonical_json(old_task),task_row[0]))
                db.commit()
            finally:
                db.close()
            run_snapshot=store.get_run(old_run)
            grant=store.authorize_author_successor(run_id=old_run,generation=1,
                expected_sequence=run_snapshot['run']['sequence'],caller='owner',command_id='successor-grant',
                worktree=str(case.repo),predecessor_session_id=case.context.authenticated_author['session_id'],
                successor_session_id='successor-session')
            self.assertEqual(1,grant['evaluation_generation'])
            self.assertEqual(grant,store.get_author_successor_grant(run_id=old_run,successor_session_id='successor-session'))
            stale_db=store._connect()
            try:
                stale_db.execute('UPDATE runs SET current_generation=2 WHERE id=?',(old_run,)); stale_db.commit()
            finally: stale_db.close()
            self.assertIsNone(store.get_author_successor_grant(run_id=old_run,successor_session_id='successor-session'))
            stale_db=store._connect()
            try:
                stale_db.execute('UPDATE runs SET current_generation=1 WHERE id=?',(old_run,)); stale_db.commit()
            finally: stale_db.close()
            original_frozen = self._all_submission_artifacts(store, "initial")
            original_bytes = {a["artifact"]["artifact_id"]:Path(a["artifact"]["path"]).read_bytes() for a in original_frozen}
            with self.subTest("old rows remain immutable"):
                self._capture_existing_rows(store)

            # A request from the correctly authenticated HTTP binding cannot claim another
            # author session, even when it names the current failed head explicitly.
            foreign_request = copy.deepcopy(case.request)
            foreign_request.update(idempotency_key="foreign-session", replaces_run_id=old_run)
            foreign_request["author"] = dict(foreign_request["author"], session_id="not-the-bound-session")
            before_foreign = self._row_counts(store)
            denied = client.post("/v1/submit", headers=headers,
                json={"idempotency_key":"foreign-session","request":foreign_request})
            self.assertEqual(200, denied.status_code, denied.text)
            rejected = denied.json()["response"]
            self.assertEqual("REJECTED", rejected["status"])
            self.assertEqual("FAIL", rejected["result"])
            self.assertEqual("AUTHOR_MISMATCH", rejected["code"])
            after_foreign = self._row_counts(store)
            for table in ("runs", "attempts", "tasks", "events", "outbox"):
                self.assertEqual(before_foreign[table], after_foreign[table], f"foreign author changed {table}")
            db = store._connect()
            try:
                self.assertEqual(old_run, db.execute("SELECT run_id FROM feature_heads WHERE worktree=? AND sprint='sprint'",(str(case.repo),)).fetchone()[0])
                for table, before in self._preserved_rows.items():
                    self.assertTrue(before <= {tuple(row) for row in db.execute(f"SELECT * FROM {table}")},
                        f"foreign author changed existing rows in {table}")
            finally:
                db.close()

            # A fresh evidence identity/provenance and fresh scope/key bind changed source bytes.
            (case.repo / "tracked.txt").write_text("corrected", encoding="utf-8")
            retry_key = "retry"
            retry_submission = workflow_store.WorkflowStore.submission_id_for(case.caller, retry_key)
            retry_data = b"fresh retry evidence"
            retry_path = root / "retry-evidence.txt"
            retry_path.write_bytes(retry_data)
            retry_artifact = {"contract_version":1,"artifact_id":"artifact_retry","run_id":None,
                "producer_task_id":None,"type":"SOURCE_MANIFEST","path":str(retry_path),
                "sha256":hashlib.sha256(retry_data).hexdigest(),"size_bytes":len(retry_data),"immutable":True,
                "producer_kind":"SUBMISSION","producer_id":retry_submission,"submission_id":retry_submission}
            retry_request = copy.deepcopy(case.request)
            retry_request.update(idempotency_key=retry_key,replaces_run_id=old_run,
                input_digest=workflow_store.digest(workflow_preflight.capture_source(case.repo)))
            retry_request["criteria"][0]["evidence_refs"] = ["artifact_retry"]
            retry_scope = {"contract_version":1,"policy":case.policy,"artifacts":[retry_artifact],
                "submission_idempotency_key":retry_key}
            registered_retry = client.post("/v1/scope/register", headers=headers,
                json={"idempotency_key":"scope-retry","scope":retry_scope})
            self.assertEqual(200, registered_retry.status_code, registered_retry.text)
            self.assertEqual("REGISTERED", registered_retry.json()["response"]["status"])
            accepted = client.post("/v1/submit", headers=headers,
                json={"idempotency_key":retry_key,"request":retry_request})
            self.assertEqual(200, accepted.status_code, accepted.text)
            receipt = accepted.json()["response"]
            self.assertEqual("ACCEPTED", receipt["status"])
            self.assertNotEqual(old_run, receipt["run_id"])
            new_run = receipt["run_id"]
            self.assertEqual("QUEUED", store.list_tasks(run_id=new_run)[0]["state"])
            self.assertEqual(retry_data, Path(store.get_artifact("artifact_retry")["artifact"]["path"]).read_bytes())
            # Snapshot fields vary by read model; the durable request is the linkage authority.
            submissions = store._connect()
            try:
                saved = submissions.execute("SELECT request_json FROM submissions WHERE caller=? AND idem_key=?", (case.caller,retry_key)).fetchone()[0]
                self.assertEqual(old_run, workflow_store.strict_loads(saved)["replaces_run_id"])
                self.assertEqual(new_run, submissions.execute("SELECT run_id FROM feature_heads WHERE worktree=? AND sprint='sprint'",(str(case.repo),)).fetchone()[0])
                for table, before in self._preserved_rows.items():
                    after = {tuple(row) for row in submissions.execute(f"SELECT * FROM {table}")}
                    self.assertTrue(before <= after, f"preexisting rows changed in {table}")
            finally:
                submissions.close()
            self.assertEqual(original_bytes, {a["artifact"]["artifact_id"]:Path(a["artifact"]["path"]).read_bytes() for a in original_frozen})

            # Exact replay is answered before source/evidence revalidation and adds no event/run.
            (case.repo / "tracked.txt").write_text("later unrelated edit", encoding="utf-8")
            before_replay = self._row_counts(store)
            replay = client.post("/v1/submit", headers=headers,
                json={"idempotency_key":retry_key,"request":retry_request})
            self.assertEqual(200, replay.status_code, replay.text)
            self.assertEqual(receipt, replay.json()["response"])
            self.assertEqual(before_replay, self._row_counts(store))

    @staticmethod
    def _all_submission_artifacts(store, key):
        db = store._connect()
        try:
            sid = db.execute("SELECT id FROM submissions WHERE caller='author' AND idem_key=?", (key,)).fetchone()[0]
            return [workflow_store.strict_loads(row[0]) for row in db.execute("SELECT artifact_json FROM artifacts")
                if workflow_store.strict_loads(row[0])["artifact"].get("producer_id") == sid]
        finally:
            db.close()

    @staticmethod
    def _row_counts(store):
        db = store._connect()
        try:
            return {name:db.execute(f"SELECT COUNT(*) FROM {name}").fetchone()[0]
                for (name,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
        finally:
            db.close()

    def test_optional_intent_is_typed_and_unknown_fields_remain_rejected(self):
        request = {
            "contract_version": 1, "idempotency_key": "key", "sprint_id": "sprint",
            "feature_label": "feature", "author": {"session_id": "session", "worktree": "tree", "role": "author"},
            "source_worktree": "tree", "input_digest": "a" * 64, "plan_digest": "b" * 64,
            "policy_digest": "c" * 64, "purpose": "OWNER_READY", "implementation_complete": True,
            "criteria": [{"criterion_id": "c", "status": "COMPLETE", "evidence_refs": ["e"]}],
            "manual_steps": ["test"], "check_packs": [],
        }
        workflow_store.validate(request, "submission_request")
        request["replaces_run_id"] = "run_previous"
        workflow_store.validate(request, "submission_request")
        request["unexpected"] = True
        with self.assertRaises(workflow_store.ProtocolError):
            workflow_store.validate(request, "submission_request")

    def test_missing_intent_and_stale_explicit_target_are_distinct(self):
        # Transaction guard is exercised through the canonical store owner with
        # a valid minimal admission fixture; it never touches a live database.
        with tempfile.TemporaryDirectory() as tmp:
            store = workflow_store.WorkflowStore(Path(tmp) / "workflow.sqlite")
            request = self._request()
            self._submit(store, request, "one")
            with self.assertRaises(workflow_store.SubmissionHeadConflict) as missing:
                self._submit(store, dict(request, idempotency_key="two"), "two")
            self.assertEqual(workflow_store.SubmissionHeadConflict.REPLACEMENT_REQUIRED, missing.exception.reason)
            with self.assertRaises(workflow_store.SubmissionHeadConflict) as stale:
                self._submit(store, dict(request, idempotency_key="three", replaces_run_id="run_foreign"), "three")
            self.assertEqual(workflow_store.SubmissionHeadConflict.INVALID_TARGET, stale.exception.reason)

    def test_eligible_fail_is_replaced_and_exact_intent_replays(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = workflow_store.WorkflowStore(Path(tmp) / "workflow.sqlite")
            first = self._request()
            self._submit(store, first, "one")
            db = store._connect()
            try:
                db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one' AND generation=1", (
                    workflow_store.canonical_json({"contract_version":1,"run_id":"run_one","generation":1,
                        "phase":"DECIDED","result":"FAIL","closure":None,"proposed_result":None,
                        "cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}),))
                db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','COMPLETED','$.result',json('{\"decision\":\"FAIL\",\"findings\":[],\"artifact_refs\":[]}')) WHERE id='task_one'")
                db.execute("INSERT INTO deliveries(id,event_id,recipient_json,target_kind,state,payload_digest,protocol_ack) VALUES(?,?,?,?,?,?,?)",
                    ("delivery_fixture", "event_one", '{"session_id":"session"}', "AUTHOR", "ACKNOWLEDGED", "d"*64,
                     workflow_store.canonical_json({"event_id":"event_one","command_id":"ack-fixture","run_id":"run_one","evaluation_generation":1})))
                db.commit()
            finally:
                db.close()
            self._capture_existing_rows(store)
            replacement = dict(first, idempotency_key="two", replaces_run_id="run_one")
            accepted = self._submit(store, replacement, "two")
            self.assertEqual("ACCEPTED", accepted["status"])
            changed_target = dict(replacement, replaces_run_id="run_other")
            with self.assertRaises(workflow_store.IdempotencyConflict):
                self._submit(store, changed_target, "two")
            replay = store.submit(caller="author", request=replacement,
                run={"unused": True}, first_task={"unused": True}, outbox_event={"unused": True}, logical_task_key="ignored")
            self.assertEqual(accepted, replay)
            rows = store._connect()
            try:
                saved = rows.execute("SELECT request_json FROM submissions WHERE idem_key='two'").fetchone()[0]
                self.assertEqual("run_one", workflow_store.strict_loads(saved)["replaces_run_id"])
                self.assertEqual("run_two", rows.execute("SELECT run_id FROM feature_heads WHERE worktree='tree' AND sprint='sprint'").fetchone()[0])
                for table in ("runs", "attempts", "tasks", "submissions", "artifacts", "events", "deliveries", "acks"):
                    if table not in {row[0] for row in rows.execute("SELECT name FROM sqlite_master WHERE type='table'")}:
                        continue
                    before = self._preserved_rows.get(table, set())
                    after = {tuple(row) for row in rows.execute(f"SELECT * FROM {table}")}
                    self.assertTrue(before <= after, f"old rows mutated in {table}")
            finally:
                rows.close()

    def test_owner_granted_successor_can_replace_fail_but_not_without_grant(self):
        with tempfile.TemporaryDirectory() as tmp:
            store=workflow_store.WorkflowStore(Path(tmp)/'workflow.sqlite')
            self._submit(store,self._request(),'one')
            self._settle(store,'fail')
            denied=dict(self._request(),idempotency_key='ungranted',replaces_run_id='run_one')
            denied['author']=dict(denied['author'],session_id='successor')
            with self.assertRaises(workflow_store.SubmissionHeadConflict):
                self._submit(store,denied,'ungranted')
            cross_tree=dict(self._request(),idempotency_key='cross-tree',replaces_run_id='run_one')
            cross_tree['source_worktree']='other-tree'
            cross_tree['author']=dict(cross_tree['author'],worktree='other-tree',session_id='successor')
            with self.assertRaises(workflow_store.SubmissionHeadConflict):
                self._submit(store,cross_tree,'cross-tree')
            grant=store.authorize_author_successor(run_id='run_one',generation=1,expected_sequence=0,
                caller='owner',command_id='grant-successor',worktree='tree',
                predecessor_session_id='session',successor_session_id='successor')
            self.assertEqual('successor',grant['successor_session_id'])
            replacement=dict(self._request(),idempotency_key='fresh-successor',replaces_run_id='run_one')
            replacement['author']=dict(replacement['author'],session_id='successor')
            accepted=self._submit(store,replacement,'fresh-successor')
            self.assertEqual('ACCEPTED',accepted['status'])
            self.assertNotEqual('run_one',accepted['run_id'])
            self.assertEqual('session',store.get_run('run_one')['run']['author']['session_id'])
            self.assertEqual('FAIL',store.get_run('run_one')['attempt']['result'])

    def test_successor_grant_does_not_transfer_predecessor_result_ack(self):
        from test_workflow_store_delivery import DeliveryEnqueueTests
        fixture=DeliveryEnqueueTests(); fixture.setUp()
        try:
            store=fixture.store
            fixture.enqueue('delivery_ack','AUTHOR',fixture.run['author'])
            store.authorize_author_successor(run_id='run_delivery',generation=1,expected_sequence=0,
                caller='owner',command_id='grant-for-ack',worktree='worktree',
                predecessor_session_id='author-session',successor_session_id='successor-session')
            ack=fixture._ack(author_session_id='successor-session')
            with self.assertRaises(workflow_store.StaleCommand):
                store.acknowledge_delivery(ack,authenticated_session='successor-session')
            delivery=store.get_delivery('delivery_ack')
            self.assertEqual('PENDING',delivery['state'])
            self.assertIsNone(delivery['protocol_ack'])
            self.assertEqual('author-session',store.get_run('run_delivery')['run']['author']['session_id'])
        finally:
            fixture.tearDown()

    def test_authority_and_outcome_matrix_rejects_ineligible_targets(self):
        cases = ("pass", "closed", "foreign_session", "foreign_worktree", "foreign_sprint", "nonhead",
                 "non_feature", "queued", "pending_task", "held", "unknown", "owned_scope")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as tmp:
                store = workflow_store.WorkflowStore(Path(tmp) / "workflow.sqlite")
                self._submit(store, self._request(), "one")
                if case == "nonhead":
                    self._settle(store, "fail")
                    self._submit(store, dict(self._request(), idempotency_key="two"), "two", internal_target="run_one")
                self._settle(store, case)
                target_id = "run_one"
                if case == "nonhead":
                    target_id = "run_one"
                request = dict(self._request(), idempotency_key="three" if case == "nonhead" else "two", replaces_run_id=target_id)
                if case == "foreign_session":
                    request["author"] = dict(request["author"], session_id="other")
                elif case == "foreign_worktree":
                    request["source_worktree"] = "other-tree"
                    request["author"] = dict(request["author"], worktree="other-tree")
                elif case == "foreign_sprint":
                    request["sprint_id"] = "other-sprint"
                with self.assertRaises(workflow_store.SubmissionHeadConflict) as rejected:
                    self._submit(store, request, "two")
                expected = (workflow_store.SubmissionHeadConflict.ACTIVE_OR_UNSETTLED
                    if case in ("queued", "pending_task", "held", "unknown", "owned_scope")
                    else workflow_store.SubmissionHeadConflict.INVALID_TARGET)
                self.assertEqual(expected, rejected.exception.reason)

    def test_two_competing_requests_cannot_rebase_after_winner(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = workflow_store.WorkflowStore(Path(tmp) / "workflow.sqlite")
            self._submit(store, self._request(), "one")
            self._settle(store, "fail")
            barrier = threading.Barrier(2)
            outcomes = []
            lock = threading.Lock()
            def compete(suffix):
                req = dict(self._request(), idempotency_key=suffix, replaces_run_id="run_one")
                barrier.wait()
                try:
                    result = self._submit(store, req, suffix)
                    value = result["status"]
                except workflow_store.SubmissionHeadConflict as exc:
                    value = exc.reason
                with lock:
                    outcomes.append(value)
            workers = [threading.Thread(target=compete, args=(name,)) for name in ("two", "three")]
            for worker in workers: worker.start()
            for worker in workers: worker.join(timeout=15)
            self.assertTrue(all(not worker.is_alive() for worker in workers))
            self.assertCountEqual(["ACCEPTED", workflow_store.SubmissionHeadConflict.INVALID_TARGET], outcomes)
            db = store._connect()
            try:
                self.assertEqual(2, db.execute("SELECT COUNT(*) FROM runs").fetchone()[0])
            finally:
                db.close()

    @staticmethod
    def _request():
        return {"contract_version": 1, "idempotency_key": "one", "sprint_id": "sprint",
            "feature_label": "feature", "author": {"session_id": "session", "worktree": "tree", "role": "author"},
            "source_worktree": "tree", "input_digest": "a"*64, "plan_digest": "b"*64,
            "policy_digest": "c"*64, "purpose": "OWNER_READY", "implementation_complete": True,
            "criteria": [{"criterion_id": "c", "status": "COMPLETE", "evidence_refs": ["e"]}],
            "manual_steps": ["test"], "check_packs": []}

    @staticmethod
    def _settle(store, mode):
        db = store._connect()
        try:
            if mode in ("pass", "error", "closed", "queued", "pending_task", "held", "unknown", "owned_scope"):
                phase = "CLOSED" if mode == "closed" else "DECIDED"
                result = "PASS" if mode == "pass" else ("ERROR" if mode == "error" else (None if phase == "CLOSED" else "FAIL"))
                attempt = {"contract_version":1,"run_id":"run_one","generation":1,"phase":phase,"result":result,
                    "closure":"CANCELLED" if phase=="CLOSED" else None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED",
                    "pause_reason":None,"proposed_closure":None}
                db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one' AND generation=1", (workflow_store.canonical_json(attempt),))
            else:
                attempt = {"contract_version":1,"run_id":"run_one","generation":1,"phase":"DECIDED","result":"FAIL",
                    "closure":None,"proposed_result":None,"cleanup_state":"NOT_REQUIRED","pause_reason":None,"proposed_closure":None}
                db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one' AND generation=1", (workflow_store.canonical_json(attempt),))
            if mode not in ("queued", "pending_task"):
                state = "COMPLETED" if mode not in ("closed",) else "CANCELLED"
                result = None if state == "CANCELLED" else {"decision":"FAIL","findings":[],"artifact_refs":[]}
                task_json = workflow_store.strict_loads(db.execute("SELECT task_json FROM tasks WHERE id='task_one'").fetchone()[0])
                task_json["state"] = state
                task_json["result"] = result
                db.execute("UPDATE tasks SET task_json=? WHERE id='task_one'", (workflow_store.canonical_json(task_json),))
            if mode in ("held", "unknown"):
                db.execute("INSERT INTO resources VALUES(?,?,?,?,?,?,?,?,?,?)", ("claim", "resource", "run_one", 1, "task_one", 1, "identity", mode.upper(), None, "d"*64))
            if mode == "owned_scope":
                db.execute("INSERT INTO native_scopes(task_id,assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,state) VALUES(?,?,?,?,?,?)",
                    ("task_one", 1, "scope", "{}", "artifact_fixture", "OWNED"))
            if mode == "non_feature":
                run = workflow_store.strict_loads(db.execute("SELECT run_json FROM runs WHERE id='run_one'").fetchone()[0])
                run["run_kind"] = "POST_ACCEPTANCE_REGRESSION"
                run["owner_acceptance_id"] = "accept_fixture"
                db.execute("UPDATE runs SET run_json=? WHERE id='run_one'", (workflow_store.canonical_json(run),))
            db.commit()
        finally:
            db.close()

    @staticmethod
    def _capture_existing_rows(store):
        db = store._connect()
        try:
            FailedRunReplacementTests._preserved_rows = {}
            for table in ("runs", "attempts", "tasks", "submissions", "artifacts", "events", "deliveries", "acks"):
                if table in {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}:
                    FailedRunReplacementTests._preserved_rows[table] = {tuple(row) for row in db.execute(f"SELECT * FROM {table}")}
        finally:
            db.close()

    @staticmethod
    def _submit(store, request, suffix, internal_target=None):
        run_id, task_id = "run_"+suffix, "task_"+suffix
        run = {"contract_version": 1, "run_id": run_id, "sprint_id": request["sprint_id"],
            "run_kind": "FEATURE_GATE", "input_digest": request["input_digest"], "plan_digest": request["plan_digest"],
            "policy_digest": request["policy_digest"], "author": request["author"], "current_attempt": 1, "sequence": 0}
        task = {"contract_version": 1, "task_id": task_id, "run_id": run_id, "evaluation_generation": 1,
            "kind": "CHECK", "dependency_ids": [], "input_digest": request["input_digest"], "state": "QUEUED",
            "assignment_generation": 1, "assigned_session_id": None, "deadline_ms": 1000, "result": None}
        event = {"contract_version": 1, "event_id": "event_"+suffix, "kind": "TASK_READY", "run_id": run_id,
            "evaluation_generation": 1, "task_id": task_id, "assignment_generation": 1, "input_digest": request["input_digest"]}
        result = store.submit(caller="author", request=request, run=run, first_task=task, outbox_event=event,
            logical_task_key="preflight:code_checks", replace_head_run_id=internal_target)
        if suffix == "one":
            FailedRunReplacementTests._preserved_rows = {}
            db = store._connect()
            try:
                for table in ("runs", "attempts", "tasks", "submissions", "artifacts", "events", "deliveries", "acks"):
                    if table in {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}:
                        FailedRunReplacementTests._preserved_rows[table] = {tuple(row) for row in db.execute(f"SELECT * FROM {table}")}
            finally:
                db.close()
        return result


if __name__ == "__main__":
    unittest.main()
