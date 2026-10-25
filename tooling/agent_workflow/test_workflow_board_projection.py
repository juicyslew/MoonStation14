import json
import tempfile
import unittest
from pathlib import Path

import workflow_board
from workflow_store import WorkflowStore
from workflow_store_test_support import admission_records


def record(phase, result=None, *, decisions=(), regressions=(), kind="CHECK", extra=None):
    run = {"run_id":"r", "current_attempt":2, "sequence":4, "input_digest":"digest",
           "feature_id":"feature", "title":"A considerably longer complete feature title"}
    attempt = {"generation":2,"phase":phase,"result":result,"criteria":["criterion"]}
    item = {"worktree":"/w","sprint":"S","run":run,"attempt":attempt,"request":{"title":run["title"],"criteria":[{"criterion_id":"c1","description":"criterion"}],"manual_steps":["Manual step"]},
            "tasks":[{"kind":kind,"state":"COMPLETED","result":{"decision":result}}],
            "owner_decisions":list(decisions),"acceptances":[],"regression_jobs":list(regressions),
            "incidents":[],"resources":[],"deliveries":[],"artifacts":[],"events":[]}
    if extra:
        item.update(extra)
    return item


def project(item):
    return workflow_board.project({"available":True,"captured_at_ms":123,"records":[item],"history":[]},worktree="/w")


class WorkflowBoardProjectionTests(unittest.TestCase):
    def test_terminal_error_is_not_presented_as_active_check_or_review(self):
        check=record("DECIDED","ERROR",extra={"tasks":[{"kind":"CHECK","state":"COMPLETED","result":{"decision":"ERROR","findings":[{"summary":"Service unavailable"}]}}]})
        card=project(check)["columns"]["Checking"][0]
        self.assertEqual(card["status_text"],"Stopped — validation error")
        self.assertFalse(card["is_running"])
        self.assertEqual(card["next_action"],"Repair the service or retry the existing run")
        self.assertNotEqual(card["status_text"],"Running checks")
        self.assertEqual(card["details"]["current_error"],"Service unavailable")
        review=record("DECIDED","ERROR",kind="REVIEW",extra={"tasks":[{"kind":"REVIEW","state":"COMPLETED","result":{"decision":"ERROR"}}]})
        card=project(review)["columns"]["Reviewing"][0]
        self.assertEqual(card["status_text"],"Review stopped — service error")
        self.assertFalse(card["is_running"])

    def test_actual_attempt_phase_classification_and_precedence(self):
        accepted = {"decision_id":"d","run_id":"r","generation":2,"input_digest":"digest",
                    "decision":{"decision":"ACCEPT","run_id":"r","evaluation_generation":2,"input_digest":"digest"},
                    "response":{"decision_id":"d","acceptance_id":"ac"}}
        cases = [("QUEUED",None,(),"Queued"),("CHECKING",None,(),"Checking"),
                 ("REVIEWING",None,(),"Reviewing"),("DECIDED","PASS",(),"Your testing"),
                 ("DECIDED","PASS",(accepted,),"Accepted"),
                 ("DECIDED","FAIL",(),"Building"),("DECIDED","PASS",({"decision_id":"d","generation":2,"input_digest":"digest","decision":{"decision":"REQUEST_CHANGES","run_id":"r","evaluation_generation":2,"input_digest":"digest"},"response":{"decision_id":"d","decision":"REQUEST_CHANGES","run_id":"r","evaluation_generation":2,"input_digest":"digest"}},),"Building"),
                 ("DECIDED","ERROR",(),"Reviewing"),("STOPPING",None,(),"Checking"),("CLOSED",None,(),None)]
        for phase,result,decisions,expected in cases:
            with self.subTest(phase=phase,result=result,decisions=decisions):
                extra = {"acceptances":[accepted]} if expected == "Accepted" else None
                card = project(record(phase,result,decisions=decisions,kind="REVIEW" if result=="ERROR" else "CHECK",extra=extra))
                if expected is None:
                    self.assertEqual(len(card["history"]),1)
                else:
                    self.assertEqual(len(card["columns"][expected]),1)

    def test_accepted_remains_accepted_when_regression_errors_and_faults_persist(self):
        regression_job={"contract_version":1,"regression_id":"task_regression_ac","acceptance_id":"accept_ac",
                        "run_id":"run_regression_ac","source_input_digest":"a"*64,"task_id":"task_regression_ac",
                        "status":"DECIDED","result":"ERROR"}
        item = record("DECIDED","PASS",decisions=({"decision_id":"d","generation":2,"input_digest":"digest","decision":{"decision":"ACCEPT","run_id":"r","evaluation_generation":2,"input_digest":"digest","decision_id":"d"},"response":{"decision_id":"d","acceptance_id":"accept_ac"}},),
                      regressions=({"regression_id":"task_regression_ac","acceptance_id":"accept_ac","job":regression_job,"inputs":{}},),
                      extra={"acceptances":[{"acceptance_id":"accept_ac","decision_id":"d","run_id":"r","evaluation_generation":2,"input_digest":"digest"}],
                             "incidents":[{"incident":{"incident_id":"i","state":"OPEN","summary":"pause"},"seen_ms":5,"resolved_ms":None}],
                              "resources":[{"resource_key":"native_validation","state":"UNKNOWN"}]})
        board = project(item)
        card = board["columns"]["Accepted"][0]
        self.assertEqual(card["focused_result"],"PASS")
        self.assertEqual(card["regression_state"],"ERROR")
        self.assertEqual(card["badge"],"Regression ERROR")
        self.assertEqual(card["details"]["regression_jobs"][0]["job"],{"status":"DECIDED","result":"ERROR"})
        self.assertEqual([f["state"] for f in board["faults"]],["OPEN","UNKNOWN"])
        self.assertTrue(board["faults"][0]["seen"])

    def test_whitelist_long_title_and_offline_status(self):
        item = record("DECIDED","PASS",extra={"bearer_token":"LEAK",
            "artifacts":[{"artifact":{"artifact_id":"a","type":"report","sha256":"abc","size_bytes":12,"claim_token":"LEAK"}}],
            "request":{"title":"Known title","criteria":[{"criterion_id":"c","label":"safe","access_token":"LEAK"}],"manual_steps":["safe"]}})
        board = project(item)
        encoded = json.dumps(board)
        self.assertNotIn("LEAK",encoded)
        card = board["columns"]["Your testing"][0]
        self.assertLessEqual(len(card["label"]),40)
        self.assertEqual(card["details"]["title"],"Known title")
        self.assertFalse(board["connection"]["live"])
        self.assertEqual(board["captured_at_ms"],123)
        self.assertFalse(workflow_board.project({"available":True,"records":[]},worktree="/w",connection={"live":True,"mode":"live","health":"healthy"})["connection"]["live"])

    def test_author_object_is_projected_as_only_a_scalar_session_label(self):
        item=record("QUEUED",extra={"request":{"author":{"session_id":"session-safe","worktree":"w","token":"LEAK"}},
                                  "run":{"run_id":"r","current_attempt":2,"sequence":4,"input_digest":"digest",
                                        "author":{"session_id":"run-session","credential":"LEAK"}}})
        card=project(item)["columns"]["Queued"][0]
        self.assertEqual(card["agent"],"session-safe")
        self.assertIsInstance(card["agent"],str)
        self.assertNotIn("LEAK",json.dumps(card))

    def test_typed_review_finding_retains_action_and_location_but_filters_nested_bait(self):
        finding={"criterion_id":"C1","category":"COVERAGE","message":"Missing branch test",
                 "location":"tooling/agent_workflow/workflow_board.py:42","evidence_refs":["artifact_test"],
                 "required_change":"Add a focused test for this branch","token":"LEAK",
                 "extra":{"prompt":"LEAK"},"evidence_refs_extra":[{"secret":"LEAK"}]}
        item=record("REVIEWING",kind="REVIEW",extra={"tasks":[{"kind":"REVIEW","evaluation_generation":2,
             "state":"COMPLETED","result":{"decision":"FAIL","findings":[finding]}}]})
        card=project(item)["columns"]["Reviewing"][0]
        actual=card["details"]["findings"][0]
        self.assertEqual(actual,{"criterion_id":"C1","category":"COVERAGE","message":"Missing branch test",
                                 "location":"tooling/agent_workflow/workflow_board.py:42","required_change":"Add a focused test for this branch",
                                 "evidence_refs":["artifact_test"]})
        self.assertNotIn("LEAK",json.dumps(card))

    def test_scoped_open_incident_gets_only_seen_capability_until_seen(self):
        incident={"incident_id":"incident_scoped","run_id":"r","code":"DELIVERY_UNCERTAIN",
                  "message":"Delivery needs recovery","recovery_owner":"OWNER","state":"OPEN",
                  "owner_action_required":True,"artifact_refs":[]}
        item=record("CHECKING",extra={"run":{"run_id":"r","current_attempt":2,"sequence":4,"input_digest":"digest",
                                             "author":{"worktree":"/w","session_id":"owner-session"}},
                                      "incidents":[{"incident":incident,"seen_ms":None,"resolved_ms":None}]})
        fault=project(item)["faults"][0]
        self.assertEqual(fault["capabilities"],["seen"])
        self.assertEqual(fault["bindings"],{"incident_id":"incident_scoped","run_id":"r","worktree":"/w"})
        self.assertNotIn("retry_local_api",fault["capabilities"])
        item["incidents"][0]["seen_ms"]=123
        seen=project(item)["faults"][0]
        self.assertEqual(seen["capabilities"],[])
        self.assertEqual(seen["state"],"OPEN")
        foreign=workflow_board.project({"available":True,"records":[item],"history":[]},worktree="/foreign")["faults"][0]
        self.assertEqual(foreign["capabilities"],[])

    def test_pending_review_is_not_last_task_pass_and_owner_decisions_are_generation_bound(self):
        item = record("REVIEWING",None,decisions=(
            {"decision_id":"old","decision":"REQUEST_CHANGES","run_id":"r","evaluation_generation":1,"input_digest":"digest"},
            {"decision_id":"new","decision":{"decision":"CANCEL","run_id":"r","evaluation_generation":2,"input_digest":"digest","expected_sequence":4},"run_id":"r","generation":2,"input_digest":"digest"}),
            extra={"tasks":[{"kind":"CHECK","evaluation_generation":1,"state":"COMPLETED","result":{"decision":"PASS"}},
                             {"kind":"REVIEW","state":"RUNNING","result":None}]})
        board = project(item)
        card = board["columns"]["Reviewing"][0]
        self.assertIsNone(card["focused_result"])
        self.assertEqual(card["owner_state"],"CANCEL")

    def test_owner_cancel_collapses_only_unaccepted_pass_but_acceptance_wins(self):
        cancel={"decision_id":"cancel","run_id":"r","generation":2,"input_digest":"digest",
                "decision":{"decision":"CANCEL","run_id":"r","evaluation_generation":2,"input_digest":"digest","expected_sequence":4}}
        item=record("DECIDED","PASS",decisions=(cancel,))
        board=project(item)
        self.assertFalse(board["columns"]["Your testing"])
        self.assertEqual(board["history"][0]["details"]["focused_result"],"PASS")
        accepted=dict(cancel,decision_id="accept",decision={"decision":"ACCEPT","decision_id":"accept","run_id":"r","evaluation_generation":2,"input_digest":"digest","expected_sequence":4})
        item["owner_decisions"]=[cancel,accepted]
        item["acceptances"]=[{"acceptance_id":"a","decision_id":"accept","run_id":"r","evaluation_generation":2,"input_digest":"digest"}]
        self.assertEqual(len(project(item)["columns"]["Accepted"]),1)

    def test_error_boundary_prefers_failed_or_active_task_kind_across_roles(self):
        item=record("DECIDED","ERROR",extra={"tasks":[
            {"kind":"CHECK","state":"COMPLETED","result":{"decision":"PASS"}},
            {"kind":"REVIEW","state":"COMPLETED","result":{"decision":"ERROR"}}]})
        self.assertEqual(len(project(item)["columns"]["Reviewing"]),1)
        item["attempt"]["phase"]="STOPPING"
        item["tasks"][1]["state"]="CANCEL_REQUESTED"
        self.assertEqual(len(project(item)["columns"]["Reviewing"]),1)

    def test_held_is_occupancy_not_fault_unknown_is_fault_and_resolved_kept(self):
        item = record("QUEUED",extra={"resources":[{"claim_id":"native-held","resource_key":"native_validation","state":"HELD"},
             {"claim_id":"native-unknown","resource_key":"native_validation","state":"UNKNOWN"},
             {"claim_id":"review-held","resource_key":"review-session:server:session","state":"HELD"},
             {"claim_id":"review-unknown","resource_key":"review-session:server:session","state":"UNKNOWN"}],
             "tasks":[{"task_id":"native-q","kind":"CHECK","state":"QUEUED"},{"task_id":"review-q","kind":"REVIEW","state":"QUEUED"}],
             "incidents":[{"incident":{"incident_id":"i","summary":"resolved"},"seen_ms":1,"resolved_ms":2}],
              "dependency_pauses":[{"task_id":"t","paused_since_ms":5,"dependency_key":"provider"},
                                   {"task_id":"resumed","paused_since_ms":-6,"dependency_key":"provider"}]})
        old=record("CLOSED",extra={"resources":[{"claim_id":"native-held","resource_key":"native_validation","state":"HELD"},
                                      {"claim_id":"native-unknown","resource_key":"native_validation","state":"UNKNOWN"}]})
        raw={"available":True,"records":[item],"history":[old]}
        board=workflow_board.project(raw,worktree="/w")
        self.assertEqual([f["id"] for f in board["faults"]],["native-unknown","review-unknown"])
        self.assertEqual(board["history"][0]["phase"],"Resolved incident")
        self.assertEqual(board["system"]["native_occupancy"],{"held":1,"unknown":1,"state":"unknown"})
        self.assertEqual(board["system"]["queue"],{"queued_tasks":1,"queued_native_tasks":1,
            "pending_native_tasks":0,"scope":"worktree","worktree":"/w","state":"queued"})
        self.assertEqual(board["system"]["ai_availability"],"provider_paused")
        self.assertEqual(board["columns"]["Queued"][0]["badge"],"Paused")

    def test_global_native_aggregate_and_pending_reservation_are_privacy_safe(self):
        item=record("CHECKING",extra={"tasks":[{"task_id":"local-claimed","kind":"CHECK",
            "evaluation_generation":2,"state":"CLAIMED"}],"resources":[]})
        raw={"available":True,"records":[item],"history":[],"native_capacity":{"held":1,"unknown":0}}
        board=workflow_board.project(raw,worktree="/w")
        self.assertEqual(board["system"]["native_occupancy"],{"held":1,"unknown":0,"state":"occupied"})
        self.assertEqual(board["system"]["queue"]["pending_native_tasks"],1)
        self.assertEqual(board["system"]["queue"]["queued_tasks"],1)
        raw["native_capacity"]={"held":0,"unknown":1}
        self.assertEqual(workflow_board.project(raw,worktree="/w")["system"]["native_occupancy"]["state"],"unknown")
        self.assertNotIn("foreign",json.dumps(workflow_board.project(raw,worktree="/w")))

    def test_pending_excludes_paused_cancellation_completion_and_any_own_resource_history(self):
        tasks=[{"task_id":name,"kind":"CHECK","evaluation_generation":2,"state":state}
               for name,state in (("paused","CLAIMED"),("cancel","CANCEL_REQUESTED"),("done","COMPLETED"),("own","RUNNING"))]
        item=record("CHECKING",extra={"tasks":tasks,"dependency_pauses":[{"task_id":"paused","paused_since_ms":5}],
            "resources":[{"task_id":"own","generation":2,"resource_key":"native_validation","state":"RELEASED"}]})
        board=workflow_board.project({"available":True,"records":[item],"history":[],
            "native_capacity":{"held":1,"unknown":0}},worktree="/w")
        self.assertEqual(board["system"]["queue"]["pending_native_tasks"],0)

    def test_pending_check_card_shows_capacity_wait_without_changing_phase(self):
        item=record("CHECKING",extra={"tasks":[{"task_id":"waiter","kind":"CHECK",
            "evaluation_generation":2,"state":"CLAIMED"}]})
        board=workflow_board.project({"available":True,"records":[item],"history":[],
            "native_capacity":{"held":1,"unknown":0}},worktree="/w")
        card=board["columns"]["Checking"][0]
        self.assertEqual(card["phase"],"Checking")
        self.assertEqual(card["step"],"Native capacity pending")
        self.assertEqual(card["next_action"],"Wait for native capacity")
        stopping=record("STOPPING",extra={"tasks":[{"task_id":"stopping","kind":"CHECK",
            "evaluation_generation":2,"state":"CLAIMED"}]})
        stopped=workflow_board.project({"available":True,"records":[stopping],"history":[],
            "native_capacity":{"held":1,"unknown":0}},worktree="/w")
        card=stopped["columns"]["Checking"][0]
        self.assertEqual(card["next_action"],"Complete focused checks")
        self.assertNotEqual(card["step"],"Native capacity pending")

    def test_unknown_resource_fault_keeps_higher_priority_card_action(self):
        item=record("CHECKING",extra={"tasks":[{"task_id":"waiter","kind":"CHECK",
            "evaluation_generation":2,"state":"CLAIMED"}],"resources":[{"claim_id":"own-unknown",
            "task_id":"owner","generation":2,"resource_key":"native_validation","state":"UNKNOWN"}]})
        board=workflow_board.project({"available":True,"records":[item],"history":[],
            "native_capacity":{"held":1,"unknown":1}},worktree="/w")
        card=board["columns"]["Checking"][0]
        self.assertEqual(card["badge"],"UNKNOWN")
        self.assertNotEqual(card["next_action"],"Wait for native capacity")
        self.assertNotEqual(card["step"],"Native capacity pending")

    def test_delivery_progress_is_not_a_fault_and_fault_metadata_is_preserved(self):
        item=record("REVIEWING",extra={"deliveries":[
            {"delivery_id":"pending","state":"PENDING","transport_receipt":None,"protocol_ack":None},
            {"delivery_id":"transport","state":"TRANSPORT_ACCEPTED","transport_receipt":{"receipt_id":"safe"},"protocol_ack":None}],
            "incidents":[{"incident":{"incident_id":"fault","code":"DELIVERY_UNCERTAIN","message":"uncertain","recovery_owner":"SYSTEM","owner_action_required":True,"state":"OPEN","next_retry_ms":500},"seen_ms":7,"resolved_ms":None}]})
        board=project(item)
        self.assertEqual(len(board["faults"]),1)
        fault=board["faults"][0]
        self.assertEqual((fault["code"],fault["recovery_owner"],fault["owner_action_required"],fault["next_retry_ms"]),("DELIVERY_UNCERTAIN","SYSTEM",True,500))
        self.assertTrue(fault["seen"])
        self.assertIsNone(board["columns"]["Reviewing"][0]["badge"])
        self.assertEqual([d["state"] for d in board["columns"]["Reviewing"][0]["details"]["deliveries"]],["PENDING","TRANSPORT_ACCEPTED"])

    def test_all_columns_and_closed_history_are_present(self):
        items = [record("QUEUED"),record("CHECKING"),record("REVIEWING",kind="REVIEW"),
                 record("DECIDED","PASS"),record("DECIDED","PASS",decisions=({"decision_id":"d","generation":2,"input_digest":"digest","decision":{"decision":"ACCEPT","run_id":"r","evaluation_generation":2,"input_digest":"digest","decision_id":"d"},"response":{"decision_id":"d","acceptance_id":"a"}},),
                 extra={"acceptances":[{"acceptance_id":"a","decision_id":"d","run_id":"r","evaluation_generation":2,"input_digest":"digest"}]}),
                 record("DECIDED","FAIL"),record("CLOSED")]
        board = workflow_board.project({"available":True,"records":items,"history":[]},worktree="/w")
        self.assertTrue(all(board["columns"][name] for name in workflow_board.PHASES if name != "Idle"))
        self.assertFalse(board["columns"]["Idle"])  # no synthetic unsubmitted activity without an observer
        self.assertEqual(board["history"][0]["phase"],"Closed")

    def test_unavailable_is_not_an_empty_healthy_board(self):
        board = workflow_board.project({"available":False,"reason":"database_missing",
            "unscoped_incidents":{"open":4,"recovering":2,"total":6}},worktree="/w")
        self.assertFalse(board["available"])
        self.assertNotIn("unscoped_incidents",board)
        self.assertEqual(board["unavailable_reason"],"database_missing")
        self.assertEqual(board["columns"],{name:[] for name in workflow_board.PHASES})

    def test_unscoped_rejection_count_comes_from_real_store_and_never_exposes_raw_request(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/"state.sqlite"
            store=WorkflowStore(path)
            for index,state in enumerate(("OPEN","RECOVERING")):
                incident_id=f"incident_unscoped_{index}"
                request={"idempotency_key":f"bad-{index}","source_worktree":"C:/foreign/private",
                         "caller_token":"DO_NOT_LEAK","path":"C:/foreign/private/file"}
                response={"contract_version":1,"status":"REJECTED","result":"ERROR","code":"INVALID",
                          "incident_ref":incident_id,"next_action":"WAIT_FOR_RECOVERY"}
                incident={"contract_version":1,"incident_id":incident_id,"code":"INVALID",
                          "message":"PRIVATE_RAW_MESSAGE","recovery_owner":"SYSTEM","state":state,
                          "owner_action_required":False,"artifact_refs":[]}
                store.record_rejection(caller=f"private-caller-{index}",idempotency_key=f"bad-{index}",
                                       raw_request=request,response=response,incident=incident)
            raw=WorkflowStore.readonly_snapshot(path,worktree="C:/safe-worktree")
            board=workflow_board.project(raw,worktree="C:/safe-worktree")
            self.assertTrue(raw["available"])
            self.assertEqual(board["unscoped_incidents"],{"open":1,"recovering":1,"total":2})
            self.assertEqual(board["faults"],[{"kind":"unscoped_admission","state":"UNKNOWN_SCOPE",
                 "summary":"2 unresolved admission faults (unscoped)","owner_action_required":False,"capabilities":[]}])
            self.assertTrue(all(not cards for cards in board["columns"].values()))
            encoded=json.dumps(board)
            for forbidden in ("DO_NOT_LEAK","PRIVATE_RAW_MESSAGE","private-caller","C:/foreign/private","incident_unscoped"):
                self.assertNotIn(forbidden,encoded)

    def test_unscoped_counts_are_omitted_when_missing_or_invalid(self):
        missing=workflow_board.project({"available":True,"records":[],"history":[]},worktree="/w")
        self.assertNotIn("unscoped_incidents",missing)
        invalid=workflow_board.project({"available":True,"records":[],"history":[],
            "unscoped_incidents":{"open":-1,"recovering":0,"total":0}},worktree="/w")
        self.assertNotIn("unscoped_incidents",invalid)

    def test_real_readonly_store_snapshot_projects_saved_head(self):
        with tempfile.TemporaryDirectory() as root:
            store=WorkflowStore(Path(root)/"state.sqlite")
            request,run,task,event=admission_records("board-real")
            store.submit(caller="board-test",request=request,run=run,first_task=task,
                         outbox_event=event,logical_task_key="initial")
            raw=WorkflowStore.readonly_snapshot(Path(root)/"state.sqlite",worktree=request["source_worktree"])
            board=workflow_board.project(raw,worktree=request["source_worktree"])
            self.assertTrue(raw["available"])
            card=board["columns"]["Queued"][0]
            self.assertEqual(card["run_id"],run["run_id"])
            self.assertEqual(card["generation"],1)
            self.assertEqual(card["label"],request["feature_label"])
            self.assertNotEqual(card["label"],request["sprint_id"])
            self.assertEqual(card["details"]["criteria"],request["criteria"])
            self.assertEqual(card["agent"],request["author"]["session_id"])
            self.assertTrue(card["revision"].startswith("source:"))
            self.assertEqual(card["details"]["input_digest"],request["input_digest"])
            self.assertFalse(board["connection"]["live"])


if __name__ == "__main__":
    unittest.main()
