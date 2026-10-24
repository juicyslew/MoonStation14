import copy
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_native_worker
from test_workflow_native_execution import admit_native_fixture


class ReviewInputLocatorTests(unittest.TestCase):
    def make_service(self, root, evidence_count=0):
        if evidence_count:
            # Build a normal admitted submission whose preflight-bound author
            # evidence is staged and hashed by the production submission path.
            import hashlib
            from preflight_test_support import make_preflight_case
            import workflow_submission
            import workflow_preflight
            import workflow_store
            import sys
            case=make_preflight_case(root,repo_name='repo',caller='input-author',key='input-case')
            case.policy['review_roles']=['implementation']
            case.policy['review_summaries']={'implementation':'Assess implementation against the agreed criterion and evidence.'}
            for i in range(evidence_count):
                data=(f'bound-evidence-{i}').encode(); path=root/f'author-evidence-{i}.json';path.write_bytes(data)
                aid=f'artifact_evidence_{i}'
                art={'contract_version':1,'artifact_id':aid,'run_id':None,'producer_task_id':None,
                    'type':'SOURCE_MANIFEST' if i==evidence_count-1 else 'PROVENANCE','path':str(path),
                    'sha256':hashlib.sha256(data).hexdigest(),'size_bytes':len(data),'immutable':True,
                    'producer_kind':'SUBMISSION','producer_id':case.submission_id,'submission_id':case.submission_id}
                case.context.artifacts.append(art);case.request['criteria'][0]['evidence_refs'].append(aid)
            case.request['input_digest']=workflow_store.digest(workflow_preflight.capture_source(case.repo))
            case.context=workflow_preflight.PreflightContext(case.context.repo,case.context.authenticated_author,
                case.context.policy,case.context.artifacts,case.context.publication_receipt_path,case.context.publication_receipt_sha256)
            case.request['policy_digest']=workflow_store.digest(case.policy)
            plan={'registry_version':1,'packs':[],'plan':{'title':'native fixture','kind':'feature',
                'acceptance_criteria':['c1'],'fixture':'manual.md','manual_steps':['Run.'],'references':{},
                'checks':[{'argv':[sys.executable,str(case.repo/'producer.py')],'rationale':'fixture',
                           'covers':['c1'],'kind':'build','artifacts':['build/output.zip']}]},'program_checks':[]}
            case.request['plan_digest']=workflow_store.digest(plan)
            with patch.object(workflow_preflight,'resolve_check_packs',return_value=plan):
                accepted=workflow_submission.submit_submission(workflow_store.WorkflowStore(root/'db.sqlite'),
                    caller=case.caller,idempotency_key=case.key,request=case.request,context=case.context,artifact_dir=root/'artifacts')
            if accepted.get('status')!='ACCEPTED':
                raise AssertionError(f'fixture submission failed: {accepted!r}')
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            # Continue through the standard helper to construct the authenticated reviewer service.
            # The helper's ordinary fixture is unnecessary here; build task/proxy below.
            original=store.get_task
            import workflow_review
            task={'task_id':'task_review_input_fixture','run_id':accepted['run_id'],
                'evaluation_generation':1,'assignment_generation':1,'input_digest':case.request['input_digest'],
                'kind':'REVIEW','logical_key':'review:implementation','assigned_session_id':'',
                'state':'QUEUED','result':None,'deadline_ms':9999999999999}
            task['assigned_session_id']=workflow_review.ReviewCoordinator._reserved_session(task['task_id'],1,1)
            return self._service_from_submission(root,case,store,accepted,task)
        case, store, accepted, check_id = admit_native_fixture(
            root, caller="input-author", key="input-case", review_roles=("implementation",))
        original = store.get_task
        review_task = {"task_id":"review-input-task", "run_id":accepted["run_id"],
            "evaluation_generation":1, "assignment_generation":1, "input_digest":case.request["input_digest"],
            "kind":"REVIEW", "logical_key":"review:implementation", "assigned_session_id":"review-session",
            "state":"QUEUED", "result":None}
        class StoreProxy:
            path=store.path
            many_refs=False
            def __getattr__(self,name): return getattr(store,name)
            def get_task(self, task_id):
                if task_id==review_task["task_id"]: return copy.deepcopy(review_task)
                return original(task_id)
            def list_tasks(self,run_id,generation=None):
                if self.many_refs:
                    return [{"kind":"CHECK","result":{"artifact_refs":["large-check"]*200}}]
                return store.list_tasks(run_id,generation)
            def get_artifact(self,ref):
                if ref=="large-check":
                    return {"artifact":{"artifact_id":"large-check","type":"CHECK_RECEIPT",
                        "path":str(root/"large-check.json"),"sha256":"a"*64,"size_bytes":1}}
                return store.get_artifact(ref)
        proxy=StoreProxy()
        token="review-token"
        reviewer_home=root/"reviewer-home";reviewer_home.mkdir()
        binding=workflow_coordinator.ReviewerBinding("review-caller",str(reviewer_home),"implementation")
        other=workflow_coordinator.ReviewerBinding("other-review-caller",str(reviewer_home),"parity")
        author=workflow_coordinator.CoordinatorBinding(case.caller,case.context)
        planner=SimpleNamespace(reviewers={"implementation":{"worktree":str(reviewer_home)}})
        planner.build_assignment=Mock(return_value={"body":{},"body_sha256":"a"*64})
        guide_path=root/'review-guide.json'
        guide_path.write_text('{"task_id":"review-input-task","assignment_artifact_refs":[],"evidence":[]}',encoding='utf-8')
        planner.review_guide=Mock(return_value={'artifact_id':'guide-fixture','type':'PROVENANCE','path':str(guide_path),
            'sha256':__import__('hashlib').sha256(guide_path.read_bytes()).hexdigest(),'size_bytes':guide_path.stat().st_size})
        import workflow_review_transport
        transport=Mock(spec=workflow_review_transport.ReviewDispatchBackend)
        transport.server="http://127.0.0.1:9"
        transport.request=Mock(return_value=(200,{"data":{"id":review_task["assigned_session_id"]}}))
        # This locator unit isolates frozen-input behavior; session identity is
        # independently exercised by the review transport contract tests.
        transport.session_matches_assignment.return_value=True
        results=SimpleNamespace(planner=planner)
        coordinator=workflow_coordinator.ExplicitCoordinator(proxy,bindings={"author-token":author},
            review_bindings={token:binding,"other-review-token":other},review_results=results,artifact_dir=root/"artifacts",
            identity_verifier=lambda _context:True)
        coordinator.review_pipeline=SimpleNamespace(transport=transport)
        return coordinator,proxy,case,accepted,token,review_task

    def _service_from_submission(self,root,case,store,accepted,task):
        # Reuse make_service's coordinator construction pattern, preserving a real
        # store and the submission's actual preflight artifacts.
        original=store.get_task
        class Proxy:
            path=store.path
            many_refs=False
            def __getattr__(self,n): return getattr(store,n)
            def get_task(self,tid): return copy.deepcopy(task) if tid==task['task_id'] else original(tid)
            def list_tasks(self,run_id,generation=None):
                rows=store.list_tasks(run_id,generation)
                for row in rows:
                    if row.get('kind')=='CHECK' and row.get('result') is None: row['result']={}
                return rows
        proxy=Proxy(); token='review-token';home=root/'reviewer-home';home.mkdir()
        binding=workflow_coordinator.ReviewerBinding('review-caller',str(home),'implementation')
        import workflow_review
        planner=workflow_review.ReviewCoordinator(proxy,reviewers={'implementation':{'worktree':str(home),'role':'reviewer'}},artifact_dir=root/'artifacts')
        import workflow_review_transport
        transport=Mock(spec=workflow_review_transport.ReviewDispatchBackend);transport.server='http://127.0.0.1:9'
        transport.request=Mock(return_value=(200,{'data':{'id':task['assigned_session_id']}}));transport.session_matches_assignment.return_value=True
        coordinator=workflow_coordinator.ExplicitCoordinator(proxy,bindings={'author-token':workflow_coordinator.CoordinatorBinding(case.caller,case.context)},
            review_bindings={token:binding},review_results=SimpleNamespace(planner=planner),artifact_dir=root/'artifacts',identity_verifier=lambda _:True)
        coordinator.review_pipeline=SimpleNamespace(transport=transport)
        return coordinator,proxy,case,accepted,token,task

    def test_status_returns_only_exact_verified_submission_input_locators(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root)
            try:
                with TestClient(coordinator.app()) as client:
                    response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                        "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
                self.assertEqual(200,response.status_code,response.text)
                body=response.json();self.assertEqual(task["task_id"],body["task"]["task_id"])
                self.assertEqual(task["input_digest"],body["inputs"]["input_digest"])
                self.assertEqual(str(case.repo),body["inputs"]["source_worktree"])
                self.assertNotEqual(str(reviewer_home:=root/"reviewer-home"),body["inputs"]["source_worktree"])
                self.assertEqual("preflight-bundle.json",Path(body["inputs"]["bundle"]["path"]).name)
                self.assertEqual("source-manifest.json",Path(body["inputs"]["source_manifest"]["path"]).name)
                self.assertFalse(Path(body["inputs"]["bundle"]["path"]).is_relative_to(case.repo))
                self.assertFalse(Path(body["inputs"]["source_manifest"]["path"]).is_relative_to(case.repo))
                self.assertEqual({"artifact_id","type","path","sha256","size_bytes"},set(body["inputs"]["bundle"]))
                self.assertEqual({"artifact_id","type","path","sha256","size_bytes"},set(body["inputs"]["source_manifest"]))
                self.assertNotIn("source_manifest",body["inputs"].get("content",{}))
                self.assertLessEqual(len(response.content),4096)
                submission=store.get_run_submission(accepted["run_id"])
                saved_paths={Path(x["artifact"]["path"]) for x in submission["artifacts"]}
                self.assertIn(Path(body["inputs"]["bundle"]["path"]),saved_paths)
                self.assertIn(Path(body["inputs"]["source_manifest"]["path"]),saved_paths)
                # Every manifest-bound artifact is an actionable immutable locator,
                # not merely an identifier advertised to the reviewer.
                bound=body["inputs"]["bound_artifacts"]
                verified={item["artifact_id"]:item for item in workflow_native_worker.load_native_inputs(store,accepted["run_id"])[1]["verified_artifacts"]}
                self.assertEqual(set(verified),{item["artifact_id"] for item in bound})
                for locator in bound:
                    self.assertEqual({"artifact_id","type","path","sha256","size_bytes"},set(locator))
                    raw=Path(locator["path"]).read_bytes()
                    self.assertEqual(locator["size_bytes"],len(raw))
                    import hashlib
                    self.assertEqual(locator["sha256"],hashlib.sha256(raw).hexdigest())
            finally: coordinator.stop()

    def test_status_uses_generated_bindings_when_author_attachments_collide_by_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root)
            original=store.get_run_submission
            def submission_with_collisions(run_id):
                saved=original(run_id)
                for artifact_type,name in (("SOURCE_MANIFEST","source-manifest.json"),("PROVENANCE","preflight-bundle.json")):
                    source=next(item["artifact"] for item in saved["artifacts"] if item["artifact"].get("type")==artifact_type)
                    path=root/("author-"+name);path.write_bytes(Path(source["path"]).read_bytes())
                    author=dict(source,artifact_id="author-attachment-"+artifact_type,path=str(path),
                        producer_kind="AUTHOR",producer_id="author",submission_id=None)
                    saved["artifacts"].append({"artifact":author})
                return saved
            store.get_run_submission=submission_with_collisions
            try:
                with TestClient(coordinator.app()) as client:
                    response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                        "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
                self.assertEqual(200,response.status_code,response.text)
                saved=original(accepted["run_id"])
                for field,artifact_type in (("bundle","PROVENANCE"),("source_manifest","SOURCE_MANIFEST")):
                    locator=response.json()["inputs"][field]
                    self.assertFalse(Path(locator["path"]).name.startswith("author-"))
                    self.assertEqual(artifact_type,locator["type"])
                    self.assertIn(locator["artifact_id"],{item["artifact"]["artifact_id"] for item in saved["artifacts"]})
            finally: coordinator.stop()

    def test_frozen_input_errors_include_bounded_safe_diagnostic(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root)
            try:
                with patch("workflow_preflight.capture_source",side_effect=ValueError("current source differs from admitted snapshot\nunsafe")):
                    with TestClient(coordinator.app()) as client:
                        response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                            "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
                self.assertEqual(409,response.status_code)
                error=response.json()["error"]
                self.assertEqual("FROZEN_INPUTS_UNAVAILABLE",error["code"])
                self.assertIn("current source differs from admitted snapshot",error["message"])
                self.assertNotIn("\n",error["message"])
                self.assertIn("Report an ERROR",error["message"])
            finally: coordinator.stop()

    def test_status_rejects_other_reviewer_and_tampered_frozen_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root)
            try:
                with TestClient(coordinator.app()) as client:
                    other=client.get("/v1/review/status",headers={"Authorization":"Bearer author-token"},params={"task_id":task["task_id"]})
                    self.assertEqual(403,other.status_code)
                    wrong_role=client.get("/v1/review/status",headers={"Authorization":"Bearer other-review-token"},params={"task_id":task["task_id"]})
                    self.assertEqual(403,wrong_role.status_code)
                    submission=store.get_run_submission(accepted["run_id"])
                    artifact=next(x["artifact"] for x in submission["artifacts"] if Path(x["artifact"]["path"]).name=="preflight-bundle.json")
                    path=Path(artifact["path"]);original=path.read_bytes()
                    try:
                        path.write_bytes(original+b"tamper")
                        response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                            "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
                    finally: path.write_bytes(original)
                self.assertEqual(409,response.status_code,response.text)
                self.assertEqual("FROZEN_INPUTS_UNAVAILABLE",response.json()["error"]["code"])
            finally: coordinator.stop()

    def test_response_cap_externalizes_check_refs_through_review_guide(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root);store.many_refs=True
            try:
                with TestClient(coordinator.app()) as client:
                    response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                        "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
                self.assertEqual(200,response.status_code,response.text)
                body=response.json();self.assertTrue(body["artifacts_externalized"])
                self.assertEqual([],body["artifacts"])
                self.assertEqual("review-guide.json",Path(body["inputs"]["guide"]["path"]).name)
                self.assertTrue(coordinator.review_results.planner.review_guide.called)
                self.assertLessEqual(len(response.content),4096)
            finally: coordinator.stop()

    def test_five_verified_artifacts_survive_reviewer_transport(self):
        """Exercise real status projection and the public bounded client formatter."""
        import hashlib, json
        import workflow_coordinator_mcp
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);coordinator,store,case,accepted,token,task=self.make_service(root,evidence_count=4)
            assignment_artifacts=workflow_native_worker.load_native_inputs(store,accepted['run_id'])[1]['verified_artifacts']
            self.assertEqual(5,len(assignment_artifacts))
            coordinator.review_results.planner.build_assignment(task['task_id'])
            with TestClient(coordinator.app()) as client:
                response=client.get("/v1/review/status",headers={"Authorization":"Bearer "+token,
                    "X-MS14-Reviewer-Session":task["assigned_session_id"]},params={"task_id":task["task_id"]})
            try:
                self.assertEqual(200,response.status_code,response.text)
                # Use exactly the formatter used for MCP's reviewer status response.
                encoded=workflow_coordinator_mcp.CoordinatorClient.encode_response(
                    "status",{"task_id":task["task_id"]},response.json())
                self.assertNotIn("response_too_large",encoded)
                body=json.loads(encoded);locators=body["inputs"]["bound_artifacts"]
                self.assertLessEqual(len(encoded.encode()),4096)
                self.assertEqual({x["artifact_id"] for x in assignment_artifacts},
                                 {x["artifact_id"] for x in locators})
                for locator in locators:
                    source=next(x for x in assignment_artifacts if x["artifact_id"]==locator["artifact_id"])
                    self.assertEqual(source["path"],locator["path"])
                    raw=Path(locator["path"]).read_bytes()
                    self.assertEqual(locator["size_bytes"],len(raw))
                    self.assertEqual(locator["sha256"],hashlib.sha256(raw).hexdigest())
            finally: coordinator.stop()

    def test_formatter_rejects_oversize_mandatory_five_artifacts_explicitly(self):
        import workflow_coordinator_mcp
        locators=[{"artifact_id":"mandatory-"+str(i),"path":"C:/"+"a"*900+str(i),
            "type":"PROVENANCE","sha256":"a"*64,"size_bytes":1} for i in range(5)]
        encoded=workflow_coordinator_mcp.CoordinatorClient.encode_response("status",{"task_id":"t"},
            {"ok":True,"inputs":{"bound_artifacts":locators}})
        result=__import__("json").loads(encoded)
        self.assertEqual("response_too_large",result["code"])
        self.assertNotIn("inputs",result)


if __name__=="__main__": unittest.main()
