"""Durable result delivery survives coordinator reconstruction and lost ACKs."""
import hashlib
import time
import unittest
from pathlib import Path
from types import SimpleNamespace

from starlette.testclient import TestClient

from test_workflow_store_delivery import DeliveryEnqueueTests
from workflow_coordinator import CoordinatorBinding, ExplicitCoordinator
from workflow_result_delivery import AuthorResultDelivery
from workflow_store import WorkflowStore, canonical_json


class ResultDeliveryRestartFaultTests(DeliveryEnqueueTests):
    def test_restart_reuses_saved_delivery_and_ack_does_not_nudge_idle_author(self):
        delivery_id = "delivery_" + hashlib.sha256(
            (self.event["event_id"] + "AUTHOR" + canonical_json(self.run["author"])).encode()
        ).hexdigest()[:24]
        self.enqueue(delivery_id, "AUTHOR", self.run["author"])
        calls = []

        def transport(server, method, path, body=None):
            calls.append((method, path, body))
            if method == "GET":
                return 200, {"id": "author-session", "directory": "worktree", "agent": "worker"}
            return 200, {"id": body["id"], "sessionID": "author-session", "type": "user",
                         "delivery": body["delivery"], "payload": {"text": body["text"], "metadata": body["metadata"]}}

        handler = AuthorResultDelivery(self.store, server="http://127.0.0.1:4096", request=transport)
        context = SimpleNamespace(authenticated_author=self.run["author"], repo="worktree")
        binding = CoordinatorBinding("caller", context)

        # First process delivers the terminal result; its protocol ACK is lost.
        first = ExplicitCoordinator(self.store, bindings={"token": binding}, artifact_dir=Path(self.tmp.name),
                                    handlers={"RESULT_AVAILABLE": handler}, identity_verifier=lambda _: True,
                                    reconcile_interval=.05)
        with TestClient(first.app()):
            deadline = time.monotonic() + 3
            while self.store.get_delivery(delivery_id)["state"] != "TRANSPORT_ACCEPTED" and time.monotonic() < deadline:
                time.sleep(.01)
            saved = self.store.get_delivery(delivery_id)
            self.assertEqual(saved["state"], "TRANSPORT_ACCEPTED")
            self.assertIsNone(saved["protocol_ack"])
            event = self.store.get_event(self.event["event_id"])
            saved_event, saved_body = event["payload"], calls[-1][2]

        # Reopen exactly the same sqlite database and let a reconstructed coordinator reconcile.
        self.store.close()
        self.store = WorkflowStore(Path(self.tmp.name) / "delivery.sqlite")
        handler = AuthorResultDelivery(self.store, server="http://127.0.0.1:4096", request=transport)
        second = ExplicitCoordinator(self.store, bindings={"token": binding}, artifact_dir=Path(self.tmp.name),
                                     handlers={"RESULT_AVAILABLE": handler}, identity_verifier=lambda _: True,
                                     reconcile_interval=.05)
        with TestClient(second.app()) as client:
            time.sleep(.15)
            self.assertEqual(self.store.get_event(self.event["event_id"])["payload"], saved_event)
            self.assertEqual(self.store.get_delivery(delivery_id)["payload"], saved_event)
            self.assertEqual([call[2] for call in calls if call[0] == "POST"], [saved_body])
            self.assertEqual(sum(call[0] == "GET" for call in calls), 1)

            ack = {"contract_version": 1, **{key: saved_event[key] for key in
                   ("event_id", "run_id", "evaluation_generation", "result_id", "result_digest")},
                   "command_id": "restart-ack"}
            response = client.post("/v1/acknowledge-result", headers={"Authorization": "Bearer token"}, json=ack)
            self.assertEqual(response.status_code, 200, response.text)
            time.sleep(.15)
            self.assertEqual(self.store.get_delivery(delivery_id)["state"], "ACKNOWLEDGED")
            self.assertEqual([call[2] for call in calls if call[0] == "POST"], [saved_body])
            self.assertEqual(sum(call[0] == "GET" for call in calls), 1)


if __name__ == "__main__":
    unittest.main()
