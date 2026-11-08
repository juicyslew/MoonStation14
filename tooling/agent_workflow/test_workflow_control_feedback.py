"""End-to-end controls use the saved workflow store and strict local API fakes."""
import tempfile
import unittest
from pathlib import Path

import test_workflow_regression as regression_fixtures
import test_workflow_store_owner as owner_fixtures
from workflow_result_delivery import AuthorResultDelivery, _feedback_text


class ControlFeedbackTests(unittest.TestCase):
    def test_owner_accept_feedback_is_projection_only_without_author_post(self):
        fixture=owner_fixtures.OwnerAcceptanceTests("test_acceptance_and_exact_replay_create_one_job_and_wake")
        fixture.setUp()
        try:
            source,build,result_id=fixture.acceptance_setup()
            response=fixture.perform_accept(source,build,result_id)
            event_id="event_owner_feedback_"+response["decision_id"][6:]
            event=fixture.store.get_event(event_id)["payload"]
            self.assertEqual("REGRESSION_QUEUED",event["next_action"])
            sender,calls=self._sender(fixture.store,fixture.store.get_run("run_finality")["run"]["author"])
            self.assertEqual("APPLIED",sender.observe(event_id,event))
            self.assertEqual("PROJECTION_ONLY",sender.dispatch(event_id,event))
            self.assertEqual([],calls)
            self.assertEqual("accept_finality",fixture.store.get_acceptance("accept_finality")["acceptance_id"])
            self.assertIsNotNone(fixture.store.get_regression_job(response["regression_id"]))
        finally: fixture.tearDown()

    def _sender(self, store, author):
        calls=[]
        def request(server,method,path,body=None):
            calls.append((method,body))
            if method=="GET": return 200,{"id":author["session_id"],"directory":author["worktree"],"agent":"author-worker"}
            return 200,{"id":body["id"],"sessionID":author["session_id"],"type":"user","delivery":body["delivery"],
                        "payload":{"text":body["text"],"metadata":body["metadata"]}}
        return AuthorResultDelivery(store,server="http://127.0.0.1:4096",request=request),calls

    def _send_and_ack(self, store, author, event):
        sender,calls=self._sender(store,author)
        self.assertEqual("TRANSPORT_ACCEPTED",sender.dispatch(event["event_id"],event))
        self.assertEqual(["GET","POST"],[method for method,_ in calls])
        text=calls[-1][1]["text"]
        self.assertIn('"event_id":"'+event["event_id"]+'"',text)
        ack={"contract_version":1,"command_id":"ack_control_"+event["event_id"],"event_id":event["event_id"],
             "run_id":event["run_id"],"evaluation_generation":event["evaluation_generation"],
             "result_id":event["result_id"],"result_digest":event["result_digest"],"author_session_id":author["session_id"]}
        result=store.acknowledge_delivery(ack,authenticated_session=author["session_id"])
        self.assertEqual("ACKNOWLEDGED",result["disposition"])
        self.assertEqual(event["next_action"],result["next_action"])
        self.assertEqual("APPLIED",sender.dispatch(event["event_id"],event))
        self.assertEqual(2,len(calls))
        return text

    def test_owner_request_changes_posts_and_acks_control_without_feature_fail(self):
        fixture=owner_fixtures.OwnerAcceptanceTests("test_request_changes_and_cancel_are_feedback_not_verdicts_or_jobs")
        fixture.setUp()
        try:
            source,build,result_id=fixture.acceptance_setup()
            fixture.perform_accept(source,build,result_id,action="REQUEST_CHANGES")
            db=fixture.store._connect()
            try: event_id=db.execute("SELECT id FROM events WHERE kind='OWNER_FEEDBACK'").fetchone()[0]
            finally: db.close()
            event=fixture.store.get_event(event_id)["payload"]
            author=fixture.store.get_run("run_finality")["run"]["author"]
            text=self._send_and_ack(fixture.store,author,event)
            self.assertIn("Owner requested changes",text)
            self.assertIn("fix it",text)
            self.assertNotIn("Workflow result: FAIL",text)
            self.assertEqual("PASS",fixture.store.get_run("run_finality")["attempt"]["result"])
            self.assertIsNone(fixture.store.get_regression_job("task_regression_finality"))
        finally: fixture.tearDown()

    def test_regression_fail_posts_bound_control_ack_and_keeps_acceptance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho 1 required tests failed\nexit /b 1\n'
            fixture=regression_fixtures.NativeRegressionTests()
            case,store,feature,accepted,admitted,frozen,backend=fixture._prepared(root,bat=bat,key="control_fail")
            proc=None
            try:
                store.transition_attempt(admitted["run_id"],1,expected_sequence=0,phase="CHECKING")
                backend.dispatch("control-failure",{"task_id":admitted["task_id"]})
                proc=backend.workers[admitted["task_id"]]; proc.wait(timeout=30)
                self.assertEqual("FAIL",store.get_task(admitted["task_id"])["result"]["decision"])
                terminal=fixture._terminal_regression(store,feature,admitted,"FAIL")
                event=store.get_event(terminal["event_id"])["payload"]
                text=self._send_and_ack(store,store.get_run(feature["run_id"])["run"]["author"],event)
                self.assertIn("Post-acceptance regression FAIL",text)
                self.assertIn("feature acceptance remains unchanged",text)
                self.assertEqual(frozen,store.get_run(feature["run_id"]))
                self.assertEqual("FAIL",store.get_regression_job(accepted["regression_id"])["result"])
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_regression_pass_is_projection_only_and_native_deadline_is_live(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); bat='@echo off\necho GameTest tag preflight: 3/3 fixtures selected\necho All 3 required tests passed\nexit /b 0\n'
            fixture=regression_fixtures.NativeRegressionTests()
            case,store,feature,accepted,admitted,frozen,backend=fixture._prepared(root,bat=bat,key="control_pass")
            proc=None
            try:
                import time
                now=int(time.time()*1000); deadline=store.get_task(admitted["task_id"])["deadline_ms"]
                self.assertGreater(deadline,now)
                self.assertLessEqual(deadline,now+3600000+5000)
                store.transition_attempt(admitted["run_id"],1,expected_sequence=0,phase="CHECKING")
                backend.dispatch("control-pass",{"task_id":admitted["task_id"]})
                proc=backend.workers[admitted["task_id"]]; proc.wait(timeout=30)
                terminal=fixture._terminal_regression(store,feature,admitted,"PASS")
                # Derive the exact saved PASS event rather than assuming a delivery exists.
                db=store._connect()
                try: payload=__import__('json').loads(db.execute("SELECT payload FROM events WHERE kind='REGRESSION_DECIDED'").fetchone()[0])
                finally: db.close()
                sender,calls=self._sender(store,store.get_run(feature["run_id"])["run"]["author"])
                self.assertEqual("APPLIED",sender.observe(payload["event_id"],payload))
                self.assertEqual("PROJECTION_ONLY",sender.dispatch(payload["event_id"],payload))
                self.assertEqual([],calls)
                self.assertEqual(frozen,store.get_run(feature["run_id"]))
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)
