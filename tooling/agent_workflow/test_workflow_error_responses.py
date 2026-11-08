import unittest
import ast
import tempfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_store
import workflow_submission
from workflow_store_test_support import admission_records, admit


class ErrorResponseTests(unittest.TestCase):
    def test_action_families_have_bounded_recovery(self):
        cases = {
            "MALFORMED_REQUEST": "CORRECT_REQUEST",
            "INVALID_QUERY": "CORRECT_REQUEST",
            "STALE_COMMAND": "REFRESH_STATUS",
            "FROZEN_INPUTS_UNAVAILABLE": "REPORT_ERROR",
            "IDENTITY_DENIED": "VERIFY_CURRENT_SESSION",
            "INTAKE_HELD": "WAIT_FOR_ADMISSION",
            "SYSTEM_FAILURE": "WAIT_FOR_RECOVERY",
            "ACK_CONFLICT": "CHECK_RESULT_STATUS",
            "ACKNOWLEDGEMENT_UNAVAILABLE": "CHECK_RESULT_STATUS",
            "STALE_OWNER_VIEW": "REFRESH_OWNER_VIEW",
            "SUBMISSION_REPLACEMENT_UNAVAILABLE": "PREPARE_FAILED_RUN_REPLACEMENT",
        }
        for code, action in cases.items():
            with self.subTest(code=code):
                result = workflow_coordinator._error(code, "safe")['error']
                self.assertEqual(result['next_action'], action)
                self.assertTrue(result['recovery'])
                self.assertLess(len(result['recovery']), 501)

    def test_existing_head_conflict_is_durable_and_guidance_visible_without_state_replacement(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            store=workflow_store.WorkflowStore(root/"state.sqlite")
            first_request,first_run,first_task,first_event=admission_records("head-old")
            worktree=str(root/"repo")
            first_request['source_worktree']=worktree
            first_request['author']['worktree']=worktree
            first_run['author']=first_request['author']
            store.submit(caller="caller",request=first_request,run=first_run,first_task=first_task,outbox_event=first_event,logical_task_key="initial")
            request,run,task,event=admission_records("head-new",idempotency_key="new-request-key")
            request['source_worktree']=worktree
            request['author']['worktree']=worktree
            run['author']=request['author']
            context=SimpleNamespace(repo=request['source_worktree'],authenticated_author=request['author'])

            def attempt_store_admission(_store, caller, idempotency_key, req, req_digest, context, base, replace_head_run_id):
                candidate_request,candidate_run,candidate_task,candidate_event=admission_records("head-new-"+idempotency_key,idempotency_key=idempotency_key)
                candidate_request['source_worktree']=worktree
                candidate_request['author']['worktree']=worktree
                candidate_run['author']=candidate_request['author']
                return store.submit(caller=caller,request=req,run=candidate_run,first_task=candidate_task,
                    outbox_event=candidate_event,logical_task_key="initial",replace_head_run_id=replace_head_run_id)

            with patch.object(workflow_submission,"_submit_verified",side_effect=attempt_store_admission):
                receipt=workflow_submission.submit_submission(store,caller="caller",idempotency_key="new-request-key",
                    request=request,context=context,artifact_dir=root/"artifacts")
                invalid_request=dict(request,idempotency_key="invalid-target",replaces_run_id="run_foreign")
                invalid=workflow_submission.submit_submission(store,caller="caller",idempotency_key="invalid-target",
                    request=invalid_request,context=context,artifact_dir=root/"artifacts")
            self.assertEqual(receipt['status'],"REJECTED")
            self.assertEqual(receipt['result'],"FAIL")
            self.assertEqual(receipt['code'],"SUBMISSION_REPLACEMENT_REQUIRED")
            self.assertEqual(receipt['findings'][0]['location'],"replaces_run_id")
            self.assertIn("replaces_run_id",receipt['findings'][0]['required_change'])
            self.assertEqual((invalid['result'],invalid['code']),("FAIL","REPLACEMENT_TARGET_INVALID"))
            self.assertEqual(invalid['findings'][0]['location'],"replaces_run_id")
            self.assertEqual(store.get_run(first_run['run_id'])['run']['run_id'],first_run['run_id'])
            self.assertIsNone(store.get_run(run['run_id']))
            saved=store.get_submission("caller","new-request-key")
            self.assertEqual(saved,receipt)
            replay=workflow_submission.submit_submission(store,caller="caller",idempotency_key="new-request-key",
                request=request,context=context,artifact_dir=root/"artifacts")
            self.assertEqual(replay,receipt)

            binding=workflow_coordinator.CoordinatorBinding("caller",context)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={"token":binding},artifact_dir=root/"artifacts",
                handlers={},identity_verifier=lambda _:True)
            client=TestClient(coordinator.app()) # no lifespan: do not start a task dispatcher
            headers={"Authorization":"Bearer token"}
            submitted=client.post("/v1/submit",headers=headers,json={"idempotency_key":"new-request-key","request":request})
            status=client.get("/v1/status?idempotency_key=new-request-key",headers=headers)
            for result in (submitted.json(),status.json()):
                visible=result.get('response',result)
                if 'submission' in visible: visible=visible['submission']
                self.assertEqual(visible['status'],"REJECTED")
                self.assertEqual(visible['result'],"FAIL")
                self.assertEqual(visible['code'],"SUBMISSION_REPLACEMENT_REQUIRED")
                self.assertEqual(visible['findings'][0]['location'],"replaces_run_id")
                self.assertIn("replaces_run_id",visible['findings'][0]['required_change'])
            self.assertEqual(store.get_submission("caller","new-request-key"),receipt)

    def test_all_http_error_call_sites_have_action_policy_or_explicit_action(self):
        path = Path(workflow_coordinator.__file__)
        tree = ast.parse(path.read_text(encoding="utf-8"))
        uncovered=[]
        count=0
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Name) or node.func.id != "_error":
                continue
            if not node.args or not isinstance(node.args[0], ast.Constant) or not isinstance(node.args[0].value, str):
                continue
            count += 1
            code=node.args[0].value
            explicit=len(node.args)>2 or any(k.arg == "recovery" for k in node.keywords)
            if not explicit and code not in workflow_coordinator._ERROR_ACTIONS:
                uncovered.append(code)
        self.assertGreater(count, 70)
        self.assertEqual(uncovered, [])

    def test_acknowledgement_store_failure_is_uncertain_not_correctable(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = workflow_store.WorkflowStore(Path(tmp) / "state.sqlite")
            context=SimpleNamespace(authenticated_author={"session_id":"session-safe"})
            binding=workflow_coordinator.CoordinatorBinding("caller",context)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={"token":binding},artifact_dir=tmp,handlers={},identity_verifier=lambda _:True)
            with patch.object(store,"acknowledge_delivery",side_effect=RuntimeError("backend unavailable")):
                with TestClient(coordinator.app()) as client:
                    response=client.post("/v1/acknowledge-result",headers={"Authorization":"Bearer token"},json={"event_id":"event-safe"})
            self.assertEqual(response.status_code,503)
            error=response.json()['error']
            self.assertEqual(error['code'],"ACKNOWLEDGEMENT_UNAVAILABLE")
            self.assertEqual(error['next_action'],"CHECK_RESULT_STATUS")
            self.assertIn("may have committed",error['recovery'])

    def test_owner_cancel_stale_command_includes_only_bounded_sanitized_store_message(self):
        with tempfile.TemporaryDirectory() as tmp:
            store=workflow_store.WorkflowStore(Path(tmp)/"state.sqlite")
            root=Path(tmp).resolve()
            binding=workflow_coordinator.OwnerBinding("caller",str(root))
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={"author-token":workflow_coordinator.CoordinatorBinding("author-caller",SimpleNamespace(repo=str(root)))},owner_bindings={"token":binding},artifact_dir=tmp,handlers={},identity_verifier=lambda _:True)
            store.get_run=lambda _run_id:{"run":{"run_id":"run_safe","author":{"worktree":str(root)}}}
            coordinator.run_control=SimpleNamespace(cancel=lambda *_: (_ for _ in ()).throw(workflow_store.StaleCommand("cancelled review readiness is unproven\n"+"x"*500)))
            command={"run_id":"run_safe","evaluation_generation":1,"expected_sequence":1,"command_id":"command_safe","reason":"CANCELLED","actor":"OWNER","contract_version":1}
            with TestClient(coordinator.app()) as client:
                response=client.post("/v1/cancel",headers={"Authorization":"Bearer token"},json=command)
            error=response.json()["error"]
            self.assertEqual(response.status_code,409)
            self.assertEqual(error["code"],"STALE_COMMAND")
            self.assertEqual(error["next_action"],"REFRESH_STATUS")
            self.assertIn("cancelled review readiness is unproven",error["message"])
            self.assertNotIn("\n",error["message"])
            self.assertLessEqual(len(error["message"]),350)

            coordinator.run_control=SimpleNamespace(cancel=lambda *_: (_ for _ in ()).throw(RuntimeError("secret credential")))
            with TestClient(coordinator.app()) as client:
                unknown=client.post("/v1/cancel",headers={"Authorization":"Bearer token"},json=command)
            self.assertEqual(unknown.status_code,400)
            self.assertNotIn("secret credential",unknown.text)

    def test_explicit_action_and_field_are_preserved(self):
        err = workflow_coordinator._error("ARTIFACT_ID_CONFLICT", "conflict", "PREPARE_NEW_SCOPE", field="artifacts.artifact_id")['error']
        self.assertEqual(err['next_action'], "PREPARE_NEW_SCOPE")
        self.assertEqual(err['field'], "artifacts.artifact_id")
        self.assertIn("fresh scope", err['recovery'])

    def test_legacy_error_shape_is_extended_without_losing_core_fields(self):
        result = workflow_coordinator._error("INVALID_QUERY", "bad")['error']
        self.assertEqual((result['code'], result['message']), ("INVALID_QUERY", "bad"))
        self.assertEqual(result['next_action'], "CORRECT_REQUEST")
        self.assertIn('recovery', result)

    def test_artifact_conflict_is_typed_idempotency_conflict(self):
        conflict = workflow_store.ArtifactIdentityConflict("artifact-safe-id", "artifacts.artifact_id")
        self.assertIsInstance(conflict, workflow_store.IdempotencyConflict)
        self.assertEqual(conflict.field, "artifacts.artifact_id")
        self.assertNotIn("private", str(conflict))

    def test_real_store_artifact_binding_conflict_exposes_only_field(self):
        with tempfile.TemporaryDirectory() as tmp:
            store=workflow_store.WorkflowStore(Path(tmp)/"state.sqlite")
            req=__import__("json").loads((Path(__file__).parents[2]/"docs/development-workflow-redesign/contracts/fixtures/positive.json").read_text(encoding="utf-8"))[0]
            submission=store.submission_id_for("caller","key")
            artifact={"contract_version":1,"artifact_id":"artifact_safe","run_id":None,"submission_id":submission,
                "type":"SOURCE_MANIFEST","path":"manifest.json","sha256":"a"*64,"size_bytes":12,"immutable":True,
                "producer_kind":"SUBMISSION","producer_id":submission,"producer_task_id":None}
            store.register_artifact(artifact,input_digest=req["input_digest"])
            with self.assertRaises(workflow_store.ArtifactIdentityConflict) as raised:
                store.register_artifact(dict(artifact,path="changed.json"),input_digest=req["input_digest"])
            self.assertEqual(raised.exception.artifact_id,"artifact_safe")
            self.assertEqual(raised.exception.field,"artifacts.artifact_id")
            self.assertNotIn("changed.json",str(raised.exception))

    def test_schema_protocol_errors_have_safe_field_and_recovery(self):
        samples = [
            ({"contract_version":1,"task_id":"task_x","run_id":"run_x","evaluation_generation":1,
              "assignment_generation":1,"input_digest":"0"*64,"decision":"PASS","findings":[],"inspected_refs":[]}, "findings"),
            ({"contract_version":1,"task_id":"task_x","run_id":"run_x","evaluation_generation":1,
              "assignment_generation":1,"input_digest":"0"*64,"decision":"NOT_A_DECISION","findings":[],"inspected_refs":["ref_x"]}, "decision"),
            ({"contract_version":1,"task_id":123,"run_id":"run_x","evaluation_generation":1,
              "assignment_generation":1,"input_digest":"0"*64,"decision":"PASS","findings":[],"inspected_refs":["ref_x"]}, "task_id"),
            ({"contract_version":1,"task_id":"task_x","run_id":"run_x","evaluation_generation":1,
              "assignment_generation":1,"input_digest":"0"*64,"decision":"PASS","findings":[],"inspected_refs":["ref_x"],"extra_private":"PRIVATE_SENTINEL"}, "review_result"),
        ]
        for value, expected in samples:
            with self.subTest(expected=expected):
                with self.assertRaises(workflow_store.ProtocolError) as raised:
                    workflow_store.validate(value,"review_result")
                self.assertTrue(raised.exception.field)
                self.assertTrue(raised.exception.recovery)
                self.assertLessEqual(len(raised.exception.field),256)
                self.assertLessEqual(len(raised.exception.recovery),500)
                self.assertNotIn("PRIVATE_SENTINEL", raised.exception.recovery)
                if expected != "review_result":
                    self.assertEqual(raised.exception.field,expected)

    def test_submit_http_artifact_conflict_has_distinct_safe_recovery(self):
        with tempfile.TemporaryDirectory() as tmp:
            import workflow_store
            coordinator_module = workflow_coordinator
            store = workflow_store.WorkflowStore(Path(tmp) / "state.sqlite")
            binding = coordinator_module.CoordinatorBinding("caller", SimpleNamespace())
            coordinator = coordinator_module.ExplicitCoordinator(store, bindings={"token": binding}, artifact_dir=tmp,
                handlers={}, identity_verifier=lambda _: True)
            app = coordinator.app()
            conflict = workflow_store.ArtifactIdentityConflict("artifact_fixture", "artifacts.artifact_id")
            with patch("workflow_submission.submit_submission", side_effect=conflict):
                with TestClient(app) as client:
                    response = client.post("/v1/submit", headers={"Authorization":"Bearer token"},
                        json={"idempotency_key":"submit-new", "request":{}})
            self.assertEqual(response.status_code, 409)
            error = response.json()["error"]
            self.assertEqual(error["code"], "ARTIFACT_ID_CONFLICT")
            self.assertEqual(error["field"], "artifacts.artifact_id")
            self.assertIn("fresh artifact IDs", error["recovery"])
            self.assertIn("fresh scope", error["recovery"])
            self.assertNotIn("artifact_fixture", response.text)
            self.assertNotIn("submit-new", response.text)
            generic = workflow_coordinator._error("IDEMPOTENCY_CONFLICT", "Key conflict")['error']
            self.assertEqual(generic['code'], "IDEMPOTENCY_CONFLICT")
            self.assertNotEqual(generic['code'], error['code'])


if __name__ == "__main__":
    unittest.main()
