"""Owner-authorized, durable run cancellation and truthful settlement."""
from __future__ import annotations

import workflow_store


class ServiceRunControl:
    def __init__(self, store, notify_committed=None):
        self.store = store
        self.notify_committed = notify_committed or (lambda: None)

    def cancel(self, command, owner_binding):
        """Close admission using the caller-bound owner identity; exact retries replay."""
        required = {"contract_version", "run_id", "evaluation_generation", "expected_sequence", "command_id", "actor", "reason"}
        if not isinstance(command, dict) or set(command) != required:
            raise workflow_store.ProtocolError("cancellation command fields are invalid")
        workflow_store.validate(command, "cancellation_command")
        if command["actor"] != "OWNER" or command["reason"] != "CANCELLED" or not isinstance(command["command_id"], str) or not command["command_id"]:
            raise workflow_store.ProtocolError("owner cancellation reason/command id is invalid")
        run_id = command["run_id"]
        saved = self.store.get_run(run_id)
        if saved is None:
            raise workflow_store.StaleCommand("run not found")
        run, attempt = saved["run"], saved["attempt"]
        if command['evaluation_generation'] != attempt['generation']:
            raise workflow_store.StaleCommand("attempt generation is stale")
        if run.get("author", {}).get("worktree") != owner_binding.worktree:
            raise PermissionError("run is outside configured owner worktree")
        if attempt.get("phase") == "DECIDED" and attempt.get("result") in ("ERROR", "FAIL"):
            retire = self.store.retire_settled_failed_head if attempt.get("result") == "FAIL" else self.store.retire_settled_error_head
            receipt = retire(run_id=run_id,
                generation=attempt["generation"], expected_sequence=command["expected_sequence"],
                caller=owner_binding.caller, command_id=command["command_id"], worktree=owner_binding.worktree)
            self.notify_committed()
            return {"contract_version":1,"cancellation_id":receipt["event_id"],
                    "run_id":run_id,"evaluation_generation":attempt["generation"],
                    "state":receipt["state"],"historical_result":receipt["historical_result"],
                    "transition":None,"child_ids":[],"waiting_task_ids":[],"cleanup_refs":[]}
        if attempt.get("phase") == "CLOSED" and attempt.get("closure") == "CANCELLED":
            # Preserve exact replay of the original STOPPING command after its
            # asynchronous settlement; transition receipts are the authority.
            if command["expected_sequence"] != saved["run"]["sequence"]:
                original=self.store.transition_attempt(run_id,attempt["generation"],
                    expected_sequence=command["expected_sequence"],phase="STOPPING",
                    closure="CANCELLED",proposed_result=None,command_id=command["command_id"],caller=owner_binding.caller)
                # Exact idempotent replay of the original cancellation command
                # takes precedence over legacy-head retirement, even after a
                # successor has been admitted.
                self.notify_committed()
                return {"contract_version":1,"cancellation_id":original["event_id"],
                        "run_id":run_id,"evaluation_generation":attempt["generation"],
                        "transition":original,"state":"CONFIRMED","child_ids":[],
                        "waiting_task_ids":[],"cleanup_refs":[]}
            receipt=self.store.retire_settled_cancelled_head(run_id=run_id,
                generation=attempt["generation"],expected_sequence=command["expected_sequence"],
                caller=owner_binding.caller,command_id=command["command_id"],worktree=owner_binding.worktree)
            self.notify_committed()
            return {"contract_version":1,"cancellation_id":receipt["event_id"],
                    "run_id":run_id,"evaluation_generation":attempt["generation"],
                    "state":receipt["state"],"historical_result":None,
                    "historical_closure":receipt["historical_closure"],
                    "transition":None,"child_ids":[],"waiting_task_ids":[],"cleanup_refs":[]}
        receipt = self.store.transition_attempt(run_id, attempt["generation"],
            expected_sequence=command["expected_sequence"], phase="STOPPING", closure="CANCELLED",
            proposed_result=None, command_id=command["command_id"], caller=owner_binding.caller)
        self.notify_committed()
        # The transition receipt is durable authority; current state is only a public projection.
        current = self.store.get_run(run_id)
        attempt = current["attempt"]
        tasks = self.store.list_tasks(run_id, attempt["generation"])
        waiting = [t["task_id"] for t in tasks if t["state"] not in ("COMPLETED", "CANCELLED")]
        resources = [r for t in tasks for r in self.store.get_task_resources(t["task_id"])]
        unresolved = bool(waiting or any(r["state"] in ("HELD", "UNKNOWN") for r in resources))
        return {"contract_version": 1, "cancellation_id": receipt["event_id"],
                "run_id": run_id, "evaluation_generation": attempt['generation'],
                "transition": receipt,
                "state": "UNRESOLVED" if unresolved and attempt['phase']=='CLOSED' else 'STOPPING' if unresolved else "CONFIRMED",
                "child_ids": [t['task_id'] for t in tasks],
                "waiting_task_ids": waiting,
                "cleanup_refs": [r.get("cleanup_proof") for r in resources if r.get("cleanup_proof")]}
