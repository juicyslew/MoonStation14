"""Author feedback transport tests use only strict local API fakes."""
import hashlib
import json
import sys
import unittest
from pathlib import Path

import gate_dispatch
from test_workflow_store_delivery import DeliveryEnqueueTests
from workflow_result_delivery import AuthorResultDelivery


class AuthorResultDeliveryTests(DeliveryEnqueueTests):
    def author_id(self):
        from workflow_store import canonical_json
        return "delivery_" + hashlib.sha256((self.event["event_id"] + "AUTHOR" + canonical_json(self.run["author"])).encode()).hexdigest()[:24]

    def backend(self, fake):
        self.enqueue(self.author_id(), "AUTHOR", self.run["author"])
        return AuthorResultDelivery(self.store, server="http://127.0.0.1:4096", request=fake)

    def test_terminal_pass_posts_exact_bound_result_once(self):
        calls = []
        def fake(server, method, path, body=None):
            calls.append((method, path, body))
            if method == "GET": return 200, {"id": "author-session", "directory": "worktree", "agent": "worker"}
            sid = "author-session"
            response = {"id": body["id"], "sessionID": sid, "type": "user", "delivery": body["delivery"],
                        "payload": {"text": body["text"], "metadata": body["metadata"]}}
            return 200, response
        backend = self.backend(fake)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "TRANSPORT_ACCEPTED")
        self.assertEqual([c[0] for c in calls], ["GET", "POST"])
        prompt = calls[-1][2]
        self.assertTrue(prompt["resume"] if "resume" in prompt else True)
        saved = self.store.get_delivery(self.author_id())
        self.assertEqual(saved["state"], "TRANSPORT_ACCEPTED")
        self.assertIsNone(saved["protocol_ack"])
        self.assertEqual(saved["payload_digest"], hashlib.sha256(json.dumps(self.event, sort_keys=True, separators=(",", ":")).encode()).hexdigest())

    def test_bad_ack_and_failed_post_create_delivery_incident_without_verdict_change(self):
        calls = []
        def failed(server, method, path, body=None):
            calls.append(method)
            if method == "GET": return 200, {"id": "author-session", "directory": "worktree", "agent": "worker"}
            raise RuntimeError("ambiguous API failure")
        backend = self.backend(failed)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "UNKNOWN")
        self.assertEqual(self.store.get_delivery(self.author_id())["state"], "PENDING")
        self.assertEqual(self.store.get_run("run_delivery")["attempt"]["result"], "PASS")
        self.assertEqual(calls, ["GET", "POST"])
        self.assertEqual(backend.observe(self.event["event_id"], self.event), "UNKNOWN")
        self.assertEqual(len(calls), 2)

    def test_wrong_session_ack_is_rejected_and_does_not_claim_receipt(self):
        def fake(server, method, path, body=None):
            if method == "GET": return 200, {"id": "author-session", "directory": "worktree", "agent": "worker"}
            return 200, {"id": body["id"], "sessionID": "other", "type": "user", "delivery": body["delivery"],
                         "payload": {"text": body["text"], "metadata": body["metadata"]}}
        backend = self.backend(fake)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "UNKNOWN")
        self.assertIsNone(self.store.get_delivery(self.author_id())["transport_receipt"])

    def test_wrong_origin_and_failed_http_status_fail_closed(self):
        calls = []
        def wrong_origin(server, method, path, body=None):
            calls.append(method)
            return 200, {"id":"author-session", "directory":"other-worktree", "agent":"worker"}
        backend = self.backend(wrong_origin)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "WAIT_FOR_RECOVERY")
        self.assertEqual(calls, ["GET"])
        self.assertEqual(self.store.get_run("run_delivery")["attempt"]["result"], "PASS")

        self.tearDown()
        self.setUp()

        def forged_error(server, method, path, body=None):
            calls.append(method)
            if method == "GET": return 200, {"id":"author-session", "directory":"worktree", "agent":"worker"}
            return 500, {"id":body["id"], "sessionID":"author-session", "type":"user", "delivery":body["delivery"],
                         "payload":{"text":body["text"], "metadata":body["metadata"]}}
        backend = self.backend(forged_error)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "UNKNOWN")
        self.assertIsNone(self.store.get_delivery(self.author_id())["transport_receipt"])

    def test_dispatch_after_transport_acceptance_does_not_post_again(self):
        calls = []
        def fake(server, method, path, body=None):
            calls.append(method)
            if method == "GET": return 200, {"id":"author-session", "directory":"worktree", "agent":"worker"}
            return 200, {"id":body["id"], "sessionID":"author-session", "type":"user", "delivery":body["delivery"],
                         "payload":{"text":body["text"], "metadata":body["metadata"]}}
        backend = self.backend(fake)
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "TRANSPORT_ACCEPTED")
        self.assertEqual(backend.dispatch(self.event["event_id"], self.event), "APPLIED")
        self.assertEqual(calls, ["GET", "POST"])

    def test_feedback_is_compact_and_preserves_findings_action_and_ack(self):
        from workflow_result_delivery import _feedback_text
        payload = dict(self.event, artifact_refs=["artifact_ref_" + str(i) for i in range(100)], findings=[{"message":"detail"}])
        original = json.dumps(payload, sort_keys=True)
        text = _feedback_text(payload)
        for key in ("contract_version", "command_id", "event_id", "run_id", "evaluation_generation", "result_id", "result_digest"):
            self.assertIn('"' + key + '"', text)
        self.assertIn("acknowledge_result", text)
        self.assertNotIn("artifact_ref_", text)
        self.assertIn("detail", text)
        self.assertIn(payload["next_action"], text)
        self.assertNotIn("Result " + payload["result_id"] + " · digest", text)
        self.assertEqual(json.dumps(payload, sort_keys=True), original)

    def test_regression_feedback_omits_artifacts_and_owner_control_remains(self):
        from workflow_result_delivery import _feedback_text
        payload = dict(self.event, control_kind="POST_ACCEPTANCE_REGRESSION", result="FAIL",
                       artifact_refs=["regression_artifact_" + str(i) for i in range(100)],
                       findings=[{"message": "regression detail"}], next_action="inspect findings")
        text = _feedback_text(payload)
        self.assertNotIn("regression_artifact_", text)
        self.assertIn("regression detail", text)
        self.assertIn("inspect findings", text)
        owner = dict(self.event, control_kind="OWNER_DECISION", findings=[{"message": "owner change"}], next_action="revise")
        owner_text = _feedback_text(owner)
        self.assertIn("owner change", owner_text)
        self.assertIn("revise", owner_text)
        self.assertIn("acknowledge_result", owner_text)

    def test_error_feedback_shows_saved_diagnostic_and_incident_without_findings(self):
        from workflow_result_delivery import _feedback_text
        payload = dict(self.event, result="ERROR", findings=[], error_code="CHECK_FAILED",
                       error_message="Producer reported an invalid result.", incident_ref="incident_abc")
        text = _feedback_text(payload)
        self.assertIn("Error (CHECK_FAILED): Producer reported an invalid result.", text)
        self.assertIn("incident_abc", text)
        self.assertIn(payload["next_action"], text)
        self.assertIn('"result_digest"', text)

    def test_error_feedback_missing_or_partial_diagnostics_is_honest(self):
        from workflow_result_delivery import _feedback_text
        payload = dict(self.event, result="ERROR", findings=[])
        text = _feedback_text(payload)
        self.assertIn("Error diagnostic unavailable; check workflow status for details.", text)
        payload["error_code"] = "WORKER_ERROR"
        text = _feedback_text(payload)
        self.assertIn("Error (WORKER_ERROR): diagnostic unavailable", text)

    def test_page_delivery_never_selected_as_author(self):
        self.enqueue("delivery_page", "WEBPAGE", {"session_id": None, "worktree": "worktree", "role": "owner"})
        backend = AuthorResultDelivery(self.store, server="http://127.0.0.1:4096", request=lambda *a, **k: self.fail("page must not call author API"))
        self.assertEqual(backend.observe(self.event["event_id"], self.event), "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
