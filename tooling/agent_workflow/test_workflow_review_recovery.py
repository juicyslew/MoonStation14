"""Review-only recovery consumes retained passing CHECK evidence."""
import tempfile
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_store
from test_workflow_review import ReviewPlanningTests


class Identity:
    server = 'http://127.0.0.1:4096'
    def __call__(self, _context): return True


class NativeMustNotRun:
    def ensure_task(self, *_): raise AssertionError('review-only recovery dispatched native work')
    def close(self): pass


class ReviewRecoveryTests(unittest.TestCase):
    def test_recovery_started_plans_reviews_from_reused_pass_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=ReviewPlanningTests(); store,run_id,tid=helper.fixture(root)
            helper.run_check(root,store,run_id,tid)
            run=store.get_run(run_id)['run']; digest=run['input_digest']
            incident={'contract_version':1,'incident_id':'incident_review_recovery','run_id':run_id,'evaluation_generation':1,'code':'ADAPTER_FAULT','message':'saved recovery fixture','recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
            seq=run['sequence']
            store.transition_attempt(run_id,1,expected_sequence=seq,phase='STOPPING',proposed_result='ERROR',incident=incident)
            store.transition_attempt(run_id,1,expected_sequence=seq+1,phase='DECIDED',terminal_result='ERROR',cleanup_state='CONFIRMED',incident=incident)
            proof={'contract_version':1,'artifact_id':'artifact_fresh','run_id':run_id,'producer_task_id':tid,'type':'PROVENANCE','path':'fresh.json','sha256':'d'*64,'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':tid}
            store.register_artifact(proof,input_digest=digest)
            checks=[t for t in store.list_tasks(run_id,1) if t['kind']=='CHECK']
            refs=[ref for ref in checks[0]['result']['artifact_refs']
                  if store.get_artifact(ref)['artifact'].get('type')=='CHECK_RECEIPT']
            recovered=store.recover_error(run_id,expected_sequence=store.get_run(run_id)['run']['sequence'],authorization={'kind':'POLICY','id':'test'},fresh_input_proof='artifact_fresh',boundary='review:implementation',recovery_event={'event_id':'event_review_recovery'},reusable_artifact_refs=refs)
            class Transport:
                def __init__(self): self.assignments=[]
                def observe(self,*_): return 'ABSENT'
                def dispatch(self,event_id,payload): self.assignments.append(payload); return 'APPLIED'
            native=NativeMustNotRun()
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'author':str(root)},artifact_dir=root/'artifacts',handlers={'TASK_READY':native},identity_verifier=Identity())
            transport=Transport()
            # Configure the production planner/pipeline while keeping transports offline.
            coordinator.artifact_dir=root/'artifacts'
            coordinator.identity_verifier=Identity()
            coordinator.notify_committed=lambda *args: None
            coordinator.transport_health=None
            pipeline=workflow_coordinator.configure_review_pipeline(coordinator,{'implementation':{'role':'reviewer','worktree':str(root)}},{'implementation':'workflow-reviewer-implementation'},{'token':workflow_coordinator.ReviewerBinding('caller',str(root),'implementation')},transport_request=lambda *_: (200,{'data':{}}))
            self.assertIs(pipeline.completion_owner,coordinator.review_retry)
            self.assertIs(pipeline.completion_owner.transport,pipeline.transport)
            self.assertIs(pipeline.completion_owner.request,pipeline.transport.request)
            pipeline.transport=transport
            event=store.get_event('event_review_recovery'); payload=event['payload']; adapter=coordinator.handlers['RECOVERY_STARTED']
            self.assertEqual(adapter.observe('event_review_recovery',dict(payload,failed_boundary='review:foreign')),'UNKNOWN')
            self.assertEqual(adapter.observe('event_review_recovery',payload),'ABSENT')
            self.assertEqual(adapter.dispatch('event_review_recovery',payload),'APPLIED')
            self.assertEqual(adapter.observe('event_review_recovery',payload),'APPLIED')
            self.assertEqual(adapter.dispatch('event_review_recovery',payload),'APPLIED')
            reviews=[t for t in store.list_tasks(run_id,recovered['generation']) if t['kind']=='REVIEW']
            self.assertTrue(reviews)
            db=store._connect()
            try: self.assertEqual(0,db.execute("SELECT COUNT(*) FROM tasks WHERE run_id=? AND generation=? AND json_extract(task_json,'$.kind')='CHECK'",(run_id,recovered['generation'])).fetchone()[0])
            finally: db.close()
            self.assertEqual(store.get_run(run_id)['attempt']['phase'],'REVIEWING')
            self.assertEqual(store.get_run(run_id)['attempt']['result'],None)
            store.close()


if __name__=='__main__': unittest.main()
