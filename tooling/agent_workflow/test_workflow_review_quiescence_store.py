import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import workflow_store
from test_workflow_review_session_claims import ReviewSessionClaimTests


class ReviewQuiescenceStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        harness=ReviewSessionClaimTests()
        self.store,self.planner,tasks=harness.prepare(self.root)
        self.addCleanup(self.store.close)
        self.task=self.store.get_task(tasks[0]['task_id'])
        assignment=self.planner.build_assignment(self.task['task_id'])
        envelope=assignment['body']['envelope']
        self.session=self.task['assigned_session_id']
        message_id='msg_'+hashlib.sha256((self.task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        self.store.dispatch_task(task_id=self.task['task_id'],event_id=envelope['event_id'],
            assigned_session_id=self.session,dispatch_envelope=envelope,
            review_session_claim={'server':'http://127.0.0.1:4096','session_id':self.session,
                'message_id':message_id,'body_sha256':assignment['body_sha256']})
        claim=self.store.get_resource_history('review-session:'+workflow_store.digest({'server':'http://127.0.0.1:4096','session_id':self.session}))[0]
        self.claim=claim

    def register_proof(self, name, mutate=None):
        task=self.task; proof=self.claim['identity_proof']
        if isinstance(proof,str): proof=json.loads(proof)
        baseline_path=self.root/(name+'-baseline.json'); baseline_path.write_text('{}',encoding='utf-8')
        baseline_id='artifact_'+name+'_baseline'
        self.store.register_artifact({'contract_version':1,'artifact_id':baseline_id,'run_id':task['run_id'],'type':'PROVENANCE','path':str(baseline_path),'sha256':hashlib.sha256(b'{}').hexdigest(),'size_bytes':2,'immutable':True,'producer_kind':'TASK','producer_id':task['task_id'],'producer_task_id':task['task_id']},input_digest=task['input_digest'])
        evidence={'purpose':'REVIEW_QUIESCENCE','task_id':task['task_id'],'run_id':task['run_id'],'generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'session_id':proof['session_id'],'message_id':proof['message_id'],'admission_proven':True,'server':proof['server'],'body_sha256':proof['body_sha256'],'inactive_confirmed':True,'message_absent':True,'baseline_ref':baseline_id,'inbox_empty':True}
        if mutate: mutate(evidence)
        raw=workflow_store.canonical_json(evidence).encode(); path=self.root/(name+'.json'); path.write_bytes(raw)
        ref='artifact_'+name
        self.store.register_artifact({'contract_version':1,'artifact_id':ref,'run_id':task['run_id'],'type':'PROVENANCE','path':str(path),'sha256':hashlib.sha256(raw).hexdigest(),'size_bytes':len(raw),'immutable':True,'producer_kind':'TASK','producer_id':task['task_id'],'producer_task_id':task['task_id']},input_digest=task['input_digest'])
        return ref,proof

    def test_file_backed_proof_keeps_active_claim_held(self):
        ref,proof=self.register_proof('valid')
        self.assertEqual('ACCEPTED',self.store.record_review_quiescence(self.task['task_id'],claim_id=self.claim['claim_id'],identity_proof=proof,artifact_ref=ref)['disposition'])
        self.assertEqual('DISPATCHING',self.store.get_task(self.task['task_id'])['state'])
        self.assertEqual(('HELD',ref),(self.store.get_resource(self.claim['claim_id'])['state'],self.store.get_resource(self.claim['claim_id'])['cleanup_proof']))

    def test_session_and_body_hash_mismatch_rejected(self):
        for field,value in (('session_id','foreign'),('body_sha256','0'*64)):
            ref,proof=self.register_proof('bad_'+field,lambda evidence,f=field,v=value:evidence.update({f:v}))
            with self.assertRaises(workflow_store.ProtocolError):
                self.store.record_review_quiescence(self.task['task_id'],claim_id=self.claim['claim_id'],identity_proof=proof,artifact_ref=ref)

    def test_dependency_pause_rejects_new_claim_callback(self):
        db=self.store._connect()
        try:
            db.execute("INSERT INTO task_dependency_pauses VALUES(?,?,?,?,?)",(self.task['task_id'],'test',1,0,'{}')); db.commit()
        finally: db.close()
        with self.assertRaises(workflow_store.StaleCommand):
            self.store.claim_task(task_id=self.task['task_id'],run_id=self.task['run_id'],evaluation_generation=self.task['evaluation_generation'],assignment_generation=self.task['assignment_generation'],input_digest=self.task['input_digest'],session_id=self.session)


if __name__=='__main__': unittest.main()
