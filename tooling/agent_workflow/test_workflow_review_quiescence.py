"""Focused real-store checks for review quiescence (store checkpoint API required)."""
import tempfile
import time
import unittest
from pathlib import Path

import workflow_review_transport
import workflow_review_cancellation


class ReviewQuiescenceTests(unittest.TestCase):
    def fixture(self):
        from test_workflow_review import ReviewPlanningTests
        tmp=tempfile.TemporaryDirectory(); root=Path(tmp.name); helper=ReviewPlanningTests()
        store,run_id,check_id=helper.fixture(root,roles=('implementation',))
        helper.run_check(root,store,run_id,check_id); planner=helper.coordinator(store,run_id)
        planner.artifact_dir=root/'review-artifacts'; planner.artifact_dir.mkdir(parents=True,exist_ok=True)
        task=planner.plan_reviews(run_id)[0]; sid=task['assigned_session_id']; role='implementation'; server='http://127.0.0.1:4096'
        class API:
            def __init__(self): self.inbox=[]; self.history=[]; self.active=False; self.calls=[]; self.fail_interrupt=False; self.session=None
            def __call__(self,host,method,path,body=None):
                self.calls.append((method,path,body)); prefix='/api/session/'+sid
                if method=='GET' and path==prefix:
                    return (200,{'data':self.session}) if self.session else (404,{'error':'not found'})
                if method=='POST' and path=='/api/session':
                    self.session={'id':body['id'],'agent':body['agent'],'directory':body['location']['directory'],
                        'metadata':body['metadata']}
                    return 200,{'data':self.session}
                if method=='GET' and path=='/api/session/active': return 200,{'data':{sid:{'type':'running'}} if self.active else {}}
                if method=='GET' and path==prefix+'/inbox': return 200,{'data':list(self.inbox)}
                if method=='GET' and path==prefix+'/message?limit=50&order=desc': return 200,{'data':sorted(self.history,key=lambda r:r['time']['created'],reverse=True),'cursor':{}}
                if method=='POST' and path==prefix+'/prompt':
                    self.active=True
                    rec={'id':body['id'],'type':'user','time':{'created':int(time.time()*1000)},'text':body['text'],'metadata':body['metadata']}
                    self.history.insert(0,rec)
                    self.inbox.append({'id':body['id'],'sessionID':sid,'type':'user','delivery':body['delivery'],'time':{'created':rec['time']['created']},'payload':{'text':body['text'],'metadata':body['metadata']}})
                    return 200,{'data':self.inbox[-1]}
                if method=='DELETE': self.inbox=[x for x in self.inbox if x['id']!=path.rsplit('/',1)[-1]]; return 204,{}
                if method=='POST' and path==prefix+'/interrupt?resume=false':
                    if self.fail_interrupt: return 503,{}
                    self.active=False; return 200,{'interrupted':True}
                raise AssertionError((method,path))
        api=API(); transport=workflow_review_transport.ReviewDispatchBackend(planner,server=server,
            agents={role:'workflow-reviewer-'+role},request=api)
        self.assertEqual('APPLIED',transport.dispatch('review-ready',{'task_id':task['task_id']}))
        owner=workflow_review_cancellation.ReviewCancellation(store,planner,transport,server=server,request=api,sleep=lambda _:None)
        return tmp,store,task,owner,api

    def test_active_review_is_drained_but_task_and_capacity_remain(self):
        tmp,store,task,owner,api=self.fixture()
        try:
            self.assertEqual('APPLIED',owner.quiesce(task['task_id']))
            saved=store.get_task(task['task_id'])
            self.assertEqual(task['task_id'],saved['task_id']); self.assertEqual(task['assigned_session_id'],saved['assigned_session_id'])
            self.assertIn(saved['state'],('CLAIMED','RUNNING','DISPATCHING'))
            self.assertEqual('HELD',store.get_task_resources(task['task_id'])[0]['state'])
            self.assertEqual([],api.inbox); self.assertFalse(api.active)
            self.assertTrue(store.task_is_dependency_paused(task['task_id']))
            self.assertEqual('REVIEW_QUIESCENCE',owner.get_quiescence_checkpoint(task['task_id'])['purpose'])
        finally: tmp.cleanup()

    def test_foreign_history_refuses_before_destructive_calls(self):
        tmp,store,task,owner,api=self.fixture()
        try:
            api.history.insert(0,{'id':'foreign','type':'user','time':{'created':int(time.time()*1000)},'text':'foreign'})
            before=len(api.calls)
            self.assertEqual('UNKNOWN',owner.quiesce(task['task_id']))
            self.assertFalse(any(m=='DELETE' or (m=='POST' and p.endswith('/interrupt?resume=false')) for m,p,_ in api.calls[before:]))
        finally: tmp.cleanup()

    def test_existing_provider_pause_is_preserved(self):
        tmp,store,task,owner,api=self.fixture()
        try:
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            store.pause_dependency_task(task_id=task['task_id'],dependency_key=owner.server+':provider.auth',
                failure_id='provider-outage',binding=binding,now_ms=123456)
            prior=store.get_task_dependency_pause(task['task_id'])
            self.assertEqual('APPLIED',owner.quiesce(task['task_id']))
            after=store.get_task_dependency_pause(task['task_id'])
            self.assertEqual(prior['dependency_key'],after['dependency_key'])
            self.assertEqual(prior['paused_since_ms'],after['paused_since_ms'])
        finally: tmp.cleanup()

    def test_queued_original_without_history_is_admitted_and_drained(self):
        tmp,store,task,owner,api=self.fixture()
        try:
            api.history.clear()
            self.assertTrue(api.inbox)
            self.assertEqual('APPLIED',owner.quiesce(task['task_id']))
            checkpoint=owner.get_quiescence_checkpoint(task['task_id'])
            self.assertTrue(checkpoint['admission_proven'])
            self.assertTrue(checkpoint['inbox_empty'])
        finally: tmp.cleanup()

    def test_unknown_resource_can_be_revalidated_and_quiesced(self):
        tmp,store,task,owner,api=self.fixture()
        try:
            api.fail_interrupt=True
            self.assertEqual('UNKNOWN',owner.quiesce(task['task_id']))
            self.assertEqual('UNKNOWN',store.get_task_resources(task['task_id'])[0]['state'])
            pause=store.get_task_dependency_pause(task['task_id'])
            api.fail_interrupt=False
            self.assertEqual('APPLIED',owner.quiesce(task['task_id']))
            self.assertEqual('HELD',store.get_task_resources(task['task_id'])[0]['state'])
            self.assertEqual(pause['dependency_key'],store.get_task_dependency_pause(task['task_id'])['dependency_key'])
            self.assertEqual(pause['paused_since_ms'],store.get_task_dependency_pause(task['task_id'])['paused_since_ms'])
            self.assertTrue(owner.get_quiescence_checkpoint(task['task_id']))
        finally: tmp.cleanup()


if __name__=='__main__': unittest.main()
