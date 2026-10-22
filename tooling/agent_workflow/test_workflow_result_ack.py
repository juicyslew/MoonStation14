"""HTTP result acknowledgements use the configured author's trusted binding."""
import hashlib
import unittest
from pathlib import Path
from types import SimpleNamespace

from starlette.testclient import TestClient

from test_workflow_store_delivery import DeliveryEnqueueTests
from workflow_coordinator import CoordinatorBinding, ExplicitCoordinator
from workflow_store import canonical_json


class ResultAckRouteTests(DeliveryEnqueueTests):
    def _app(self):
        delivery_id = "delivery_" + hashlib.sha256((self.event["event_id"] + "AUTHOR" + canonical_json(self.run["author"])).encode()).hexdigest()[:24]
        self.enqueue(delivery_id, "AUTHOR", self.run["author"])
        context = SimpleNamespace(authenticated_author=self.run["author"], repo="worktree")
        binding = CoordinatorBinding("author-caller", context)
        coordinator = ExplicitCoordinator(self.store, bindings={"author-token": binding}, artifact_dir=Path(self.tmp.name),
                                          identity_verifier=lambda _: self.fail("ACK must not query the identity API"))
        return coordinator.app()

    def _payload(self):
        return {"contract_version": 1, "event_id": self.event["event_id"], "run_id": self.event["run_id"],
                "evaluation_generation": self.event["evaluation_generation"], "result_id": self.event["result_id"],
                "result_digest": self.event["result_digest"], "command_id": "ack-command"}

    def test_valid_ack_and_exact_replay_are_accepted(self):
        with TestClient(self._app()) as client:
            headers = {"Authorization": "Bearer author-token"}
            first = client.post("/v1/acknowledge-result", json=self._payload(), headers=headers)
            self.assertEqual(first.status_code, 200)
            self.assertEqual(first.json(), {"accepted": True, "disposition": "ACKNOWLEDGED", "next_action": "WAIT_FOR_OWNER_TEST"})
            second = client.post("/v1/acknowledge-result", json=self._payload(), headers=headers)
            self.assertEqual(second.json(), {"accepted": True, "disposition": "REPLAYED", "next_action": "WAIT_FOR_OWNER_TEST"})
        delivery = self.store.get_delivery("delivery_" + hashlib.sha256((self.event["event_id"] + "AUTHOR" + canonical_json(self.run["author"])).encode()).hexdigest()[:24])
        self.assertEqual(delivery["state"], "ACKNOWLEDGED")
        self.assertEqual(self.store.get_run("run_delivery")["attempt"]["result"], "PASS")

    def test_wrong_binding_and_untrusted_author_field_are_rejected_without_state_change(self):
        with TestClient(self._app()) as client:
            bad = dict(self._payload(), result_digest="e" * 64)
            response = client.post("/v1/acknowledge-result", json=bad, headers={"Authorization": "Bearer author-token"})
            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.json()["error"]["next_action"], "CORRECT_ACK")
            self.assertIn("original delivered", response.json()["error"]["recovery"])
            forged = dict(self._payload(), author_session_id="other")
            forged_response=client.post("/v1/acknowledge-result", json=forged, headers={"Authorization": "Bearer author-token"})
            self.assertEqual(forged_response.status_code,400)
            self.assertEqual(forged_response.json()["error"]["field"],"author_session_id")
            self.assertIn("server-bound",forged_response.json()["error"]["recovery"])
            self.assertEqual(client.post("/v1/acknowledge-result", json=self._payload(), headers={"Authorization": "Bearer wrong-token"}).status_code, 403)
        saved = self.store.get_delivery("delivery_" + hashlib.sha256((self.event["event_id"] + "AUTHOR" + canonical_json(self.run["author"])).encode()).hexdigest()[:24])
        self.assertEqual(saved["state"], "PENDING")
        self.assertEqual(self.store.get_run("run_delivery")["attempt"]["result"], "PASS")


if __name__ == "__main__":
    unittest.main()
