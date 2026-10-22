import json
import tempfile
import time
import unittest
from pathlib import Path

import test_workflow_review_transport as transport_fixture
import workflow_store


class ReviewSessionClaimTests(unittest.TestCase):
    def prepare(self, root):
        from test_workflow_review import ReviewPlanningTests
        planning=ReviewPlanningTests()
        store,run_id,check_id=planning.fixture(root,roles=('implementation','coverage'))
        planning.run_check(root,store,run_id,check_id)
        planner=planning.coordinator(store,run_id,roles=('implementation','coverage'))
        planner.artifact_dir=root/'review-artifacts'
        planner.artifact_dir.mkdir(parents=True,exist_ok=True)
        tasks=planner.plan_reviews(run_id)
        return store,planner,[store.get_task(t['task_id']) for t in tasks]

    def test_fresh_role_sessions_have_independent_parallel_claims(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,planner,tasks=self.prepare(root)
            transport=transport_fixture.FakeTransport()
            import workflow_review_transport
            backend=workflow_review_transport.ReviewDispatchBackend(planner,server='http://127.0.0.1:4096',
                agents={'implementation':'workflow-reviewer-implementation',
                        'coverage':'workflow-reviewer-coverage'},request=transport)
            first,second=tasks
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':first['task_id']}))
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':second['task_id']}))
            self.assertEqual(2,len(transport.posts)); self.assertEqual(2,len(transport.create_posts))
            self.assertNotEqual(first['assigned_session_id'],second['assigned_session_id'])
            server='http://127.0.0.1:4096'
            claims=[]
            for task in (first,second):
                key='review-session:'+workflow_store.digest({'server':server,'session_id':task['assigned_session_id']})
                claim=store.get_resource_history(key)[0]
                self.assertEqual('HELD',claim['state'])
                proof=json.loads(claim['identity_proof']) if isinstance(claim['identity_proof'],str) else claim['identity_proof']
                self.assertEqual({'server','session_id','message_id','body_sha256','task_id','run_id',
                    'evaluation_generation','assignment_generation','input_digest'},set(proof))
                self.assertEqual(task['assigned_session_id'],proof['session_id'])
                claims.append((key,claim,proof))
            self.assertFalse(any(r['state'] in ('HELD','UNKNOWN') for r in store.get_resource_history('native_validation')))
            self.assertIsNotNone(backend.get_session_baseline(second,planner.build_assignment(second['task_id'])))
            artifact='artifact_review_session_cleanup'
            store.register_artifact({'contract_version':1,'artifact_id':artifact,'run_id':first['run_id'],
                'type':'PROVENANCE','path':str(root/'proof'),'sha256':'a'*64,'size_bytes':1,'immutable':True,
                'producer_kind':'TASK','producer_id':first['task_id'],'producer_task_id':first['task_id']},
                input_digest=first['input_digest'])
            key,claim,proof=claims[0]
            store.release_resource(key,claim_id=claim['claim_id'],run_id=first['run_id'],
                generation=first['evaluation_generation'],task_id=first['task_id'],
                assignment_generation=first['assignment_generation'],identity_proof=proof,cleanup_proof=artifact)
            self.assertEqual('RELEASED',store.get_resource_history(key)[0]['state'])
            self.assertEqual('HELD',store.get_resource_history(claims[1][0])[0]['state'])

    def test_conflicting_same_task_claim_proof_is_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,planner,tasks=self.prepare(root)
            task=tasks[0]; assignment=planner.build_assignment(task['task_id'])
            envelope=assignment['body']['envelope']
            claim={'server':'http://127.0.0.1:4096','session_id':task['assigned_session_id'],'message_id':'msg-original','body_sha256':assignment['body_sha256']}
            store.dispatch_task(task_id=task['task_id'],event_id=envelope['event_id'],assigned_session_id=task['assigned_session_id'],dispatch_envelope=envelope,review_session_claim=claim)
            changed=dict(claim,message_id='msg-conflict')
            with self.assertRaises(workflow_store.StoreError):
                store.dispatch_task(task_id=task['task_id'],event_id=envelope['event_id'],assigned_session_id=task['assigned_session_id'],dispatch_envelope=envelope,review_session_claim=changed)


if __name__=='__main__': unittest.main()
