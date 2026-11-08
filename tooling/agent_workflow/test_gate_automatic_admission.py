import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tooling.agent_workflow import gate_execution, gate_liveness, workflow_runtime


class AutomaticAdmissionTests(unittest.TestCase):
    def setUp(self):
        aliases=patch.dict('sys.modules', {'gate_dispatch': __import__('tooling.agent_workflow.gate_dispatch', fromlist=['*']),
                                           'gate_execution': gate_execution,
                                           'gate_liveness': gate_liveness,
                                           'owner_attention': __import__('tooling.agent_workflow.owner_attention', fromlist=['*'])})
        aliases.start(); self.addCleanup(aliases.stop)
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name); (self.root/'candidates').mkdir()
        self.repo=str((self.root/'repo').resolve()); Path(self.repo).mkdir()
        self.ident='admission-fixture'; self.snapshot='snap-admission'
        self.candidate={'id':self.ident,'snapshot_key':self.snapshot,'sprint_id':'sprint',
            'worktree':{'worktree':self.repo},'gate_plan':{'kind':'docs','checks':['focused']},
            'gate_request':{'prompt_admitted':True,'dispatch_state':'admitted','requested_at':100,
                'snapshot_key':self.snapshot,'session_id':'lead-session','origin_session_id':'author-session',
                'prompt_body':{'text':'frozen'},'prompt_id':'prompt-id'}}
        self.path=self.root/'candidates'/f'{self.ident}.json'; self.save()
        gate_liveness._write(gate_liveness._path(self.root,self.ident),{'candidate_id':self.ident,'snapshot_key':self.snapshot,
            'session_id':'lead-session','state':'observing','registered_at':100,'recovery_count':0})
        (self.root/'progress').mkdir()
        (self.root/'progress'/f'{self.ident}.json').write_text(json.dumps({'candidate_id':self.ident,'snapshot_key':self.snapshot,'phase':'admitted'}))

    def save(self): self.path.write_text(json.dumps(self.candidate))

    def run_observer(self, *, source_ok=True, head=True):
        def fail_request(server,method,path,body=None,timeout=15):
            if method=='GET' and path.startswith('/api/mcp?'):
                return 200,{'location':{'directory':str(self.root)},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]}
            raise AssertionError('unexpected non-MCP API/service request: '+path)
        self.candidate['gate_request']['central_worktree']=str(self.root); self.save()
        current=[{'worktree':self.repo,'sprint_id':'sprint','head':dict(self.candidate,id=self.ident if head else 'new-head')}]
        with patch.object(workflow_runtime,'verify_gate',side_effect=(lambda c: (None,c['gate_plan']) if source_ok else (_ for _ in ()).throw(ValueError('stale')))), \
             patch.object(gate_execution,'reconcile',return_value={'pending':0,'active':0,'blocked':0,'launched':0,'errors':[]}), \
             patch('tooling.agent_workflow.owner_attention.current_work',return_value=current), \
             patch.object(gate_liveness,'_request',side_effect=fail_request):
            return gate_liveness.reconcile(self.root,workflow=workflow_runtime,request=fail_request,now=200,pid_alive=lambda _:False)

    def test_admitted_request_queues_one_job_without_model_and_retries_same_job(self):
        first=self.run_observer(); job=gate_execution.read(self.root,self.ident)
        self.assertIsNotNone(job, (first,gate_liveness.read_health(self.root,self.ident))); self.assertEqual(job['state'],'requested')
        self.assertEqual((job['snapshot_key'],job['lead_session_id'],job['origin_session_id']),
                         (self.snapshot,'lead-session','author-session'))
        self.assertEqual(job['plan_digest'],workflow_runtime.snapshot_key(self.candidate['gate_plan']))
        claim,attempt,job_id=job['claim'],job['attempt'],job['job_id']
        self.run_observer()
        repeated=gate_execution.read(self.root,self.ident)
        self.assertEqual((repeated['claim'],repeated['attempt'],repeated['job_id']),(claim,attempt,job_id))
        self.assertEqual(first['pending'],1)
        os.environ['OPENCODE_SESSION_ID']='other-session'
        try:
            with self.assertRaisesRegex(ValueError,'caller session'):
                gate_execution.request(self.root,self.ident,workflow=workflow_runtime)
            # Observer entry point is trusted without changing/removing inherited environment.
            same=gate_execution.request_admitted_observer(self.root,self.ident,workflow=workflow_runtime)
            self.assertEqual(same['claim'],claim); self.assertEqual(os.environ['OPENCODE_SESSION_ID'],'other-session')
        finally: os.environ.pop('OPENCODE_SESSION_ID',None)
        os.environ.pop('OPENCODE_SESSION_ID',None)
        public=gate_execution.request(self.root,self.ident,workflow=workflow_runtime)
        self.assertEqual(public['claim'],claim)

    def test_legacy_missing_time_blocked_terminal_stale_and_superseded_never_queue(self):
        variants=[]
        for update in ({'dispatch_state':None},{'requested_at':None}): variants.append((update,None,True,True))
        variants += [({},'blocked',True,True),({},None,False,True),({},None,True,False),
                     ({},None,True,True,'terminal'),({},None,False,True,'stale')]
        for item in variants:
            with self.subTest(item=item):
                self.candidate['gate_request'].update(prompt_admitted=True,dispatch_state='admitted',requested_at=100)
                update,health_state,source_ok,head,*extra=item
                if update:
                    for key,value in update.items():
                        if value is None: self.candidate['gate_request'].pop(key,None)
                        else: self.candidate['gate_request'][key]=value
                if health_state:
                    h=gate_liveness.read_health(self.root,self.ident); h['state']=health_state; gate_liveness._write(gate_liveness._path(self.root,self.ident),h)
                if extra and extra[0]=='terminal': self.candidate['gate_outcome']={'result':'needs_changes'}
                self.save()
                self.run_observer(source_ok=source_ok,head=head)
                self.assertIsNone(gate_execution.read(self.root,self.ident))
                # Reset fixture for next case.
                self.candidate.pop('gate_outcome',None); self.save()
                h=gate_liveness.read_health(self.root,self.ident); h.update(state='observing',session_id='lead-session'); gate_liveness._write(gate_liveness._path(self.root,self.ident),h)
                (self.root/'progress'/f'{self.ident}.json').write_text(json.dumps({'candidate_id':self.ident,'snapshot_key':self.snapshot,'phase':'admitted'}))

    def test_interrupted_job_is_never_reset_or_requeued(self):
        job=gate_execution.request_admitted_observer(self.root,self.ident,workflow=workflow_runtime)
        job.update(state='interrupted',attempt=3,reason='quarantine')
        gate_execution._write(gate_execution._path(self.root,self.ident),job)
        with patch.object(gate_execution,'reconcile',return_value={}):
            self.run_observer()
        saved=gate_execution.read(self.root,self.ident)
        self.assertEqual((saved['state'],saved['attempt'],saved['claim']),('interrupted',3,job['claim']))


if __name__=='__main__': unittest.main()
