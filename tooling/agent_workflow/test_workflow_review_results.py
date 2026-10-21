import tempfile
import unittest
from pathlib import Path

import workflow_review_results
import workflow_store
import test_workflow_review as review_fixtures


class ReviewResultTests(unittest.TestCase):
    def setUp(self): self.helper=review_fixtures.ReviewPlanningTests()

    def admitted(self, root):
        store,run_id,check_id=self.helper.fixture(root)
        self.helper.run_check(root,store,run_id,check_id)
        planner=self.helper.coordinator(store,run_id)
        task=planner.plan_reviews(run_id)[0]
        assignment=planner.build_assignment(task['task_id'])['body']['envelope']
        store.dispatch_task(task_id=task['task_id'],event_id=assignment['event_id'],assigned_session_id=task['assigned_session_id'],dispatch_envelope=assignment)
        api=workflow_review_results.ReviewResults(store,planner)
        claim={'contract_version':1,'command_id':'claim-'+task['task_id'],**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},'session_id':task['assigned_session_id']}
        self.assertTrue(api.claim(claim,session_id=task['assigned_session_id'])['accepted'])
        task=store.get_task(task['task_id'])
        self.assertEqual('CLAIMED',task['state'])
        refs=assignment['artifact_refs']
        receipt=next(r for r in refs if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT')
        return store,run_id,task,api,receipt

    def result(self, task, receipt, decision='PASS'):
        finding={'criterion_id':'criterion-1','category':'IMPLEMENTATION','message':'Reviewed implementation evidence','location':'source tree','evidence_refs':[receipt]}
        if decision=='FAIL': finding.update(required_change='Correct the branch',location='source file',)
        return {'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},'decision':decision,'findings':[finding],'inspected_refs':[receipt]}

    def test_pass_and_fail_aggregate(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp)); reply=api.complete(self.result(task,ref),session_id=task['assigned_session_id']); self.assertTrue(reply['accepted'],reply); self.assertEqual('PASS',api.aggregate(run)['decision'])
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp)); self.assertTrue(api.complete(self.result(task,ref,'FAIL'),session_id=task['assigned_session_id'])['accepted']); self.assertEqual('FAIL',api.aggregate(run)['decision'])

    def test_invalid_category_does_not_complete_and_valid_retry_works(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp)); bad=self.result(task,ref); bad['findings'][0]['category']='PENDING_TESTS'
            reply=api.complete(bad,session_id=task['assigned_session_id']); self.assertEqual(('INVALID_REVIEW_RESULT','CORRECT_RESULT'),(reply['code'],reply['next_action']))
            self.assertEqual('CLAIMED',store.get_task(task['task_id'])['state']); self.assertTrue(api.complete(self.result(task,ref),session_id=task['assigned_session_id'])['accepted'])

    def test_wrong_session_and_immutable_conflict(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp)); result=self.result(task,ref)
            self.assertFalse(api.complete(result,session_id='wrong')['accepted']); self.assertTrue(api.complete(result,session_id=task['assigned_session_id'])['accepted'])
            changed=self.result(task,ref,'FAIL'); self.assertFalse(api.complete(changed,session_id=task['assigned_session_id'])['accepted'])

    def test_error_commits_incident_and_replays(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp))
            report={'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},'error_code':'REVIEW_SERVICE_FAULT','message':'Reviewer service failed before result','artifact_refs':[ref]}
            got=api.error(report,session_id=task['assigned_session_id']); self.assertTrue(got['accepted']); self.assertEqual('COMPLETED',store.get_task(task['task_id'])['state']); self.assertEqual('ERROR',store.get_task(task['task_id'])['result']['decision'])
            self.assertEqual('REPLAYED',store.report_task_error(report,session_id=task['assigned_session_id'])['disposition'])
            self.assertEqual('ERROR',api.aggregate(run)['decision'])

    def test_guide_error_template_is_accepted_for_claimed_task(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,run,task,api,_ref=self.admitted(root)
            planner=self.helper.coordinator(store,run)
            report=planner.error_report_template(task['task_id'],error_code='FIXTURE_FAULT',message='Contained fixture fault')
            workflow_store.validate(report,'task_error')
            self.assertNotIn('session_id',report)
            reply=api.error(report,session_id=task['assigned_session_id'])
            self.assertTrue(reply['accepted'],reply)

    def test_waiting_review_is_pending_before_deadline(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,ref=self.admitted(Path(tmp)); self.assertEqual((None,[task['task_id']]),(api.aggregate(run,now_ms=0)['decision'],api.aggregate(run,now_ms=0)['waiting_task_ids']))


if __name__=='__main__': unittest.main()
