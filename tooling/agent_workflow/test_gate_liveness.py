import json
import os
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from tooling.agent_workflow import gate_liveness as live
from tooling.agent_workflow import workflow_runtime as runtime


class GateLivenessTests(unittest.TestCase):
    def setUp(self):
        # Production uses bare installed-layout imports when run from this
        # directory. Keep those names on the same module objects the fixtures
        # patch via the package namespace, and restore them after each case.
        from tooling.agent_workflow import gate_dispatch, gate_execution
        self._dependency_aliases = patch.dict('sys.modules', {
            'gate_dispatch': gate_dispatch,
            'gate_execution': gate_execution,
        })
        self._dependency_aliases.start()
        self.addCleanup(self._dependency_aliases.stop)

    def with_healthy_mcp(self, candidate, request):
        """Provide only the bound MCP health probe; preserve all bespoke API behavior."""
        central = candidate['gate_request']['central_worktree']
        def wrapped(server, method, path, body=None, timeout=15):
            if method == 'GET' and path.startswith('/api/mcp?'):
                query = parse_qs(urlsplit(path).query)
                if query.get('location[directory]') == [central]:
                    return 200, {'location': {'directory': central},
                                 'data': [{'name': 'ms14_workflow', 'status': {'status': 'connected'}}]}
            return request(server, method, path, body, timeout)
        return wrapped

    def setup_gate(self, d, *, outcome=None, prompt_id=None):
        repo = str((Path(d) / 'author-repo').resolve())
        central = str((Path(d) / 'neutral-05').resolve())
        c = {'id': 'candidate', 'snapshot_key': 'snap', 'sprint_id': 's', 'revision_sequence': 1,
             'worktree': {'worktree': repo}, 'gate_request': {'session_id': 'lead', 'snapshot_key': 'snap',
             'prompt_admitted': True, 'prompt_id': prompt_id, 'central_worktree': central}, 'gate_outcome': outcome}
        runtime.writejson(Path(d) / 'candidates' / 'candidate.json', c)
        live.register(d, c, lead_session_id='lead', workflow=runtime)
        wf = SimpleNamespace(readjson=runtime.readjson, verify_gate=lambda c: c)
        def req(server, method, path, body=None, timeout=15):
            if path.startswith('/api/mcp?'):
                return 200, {'location': {'directory': central}, 'data': [{'name': 'ms14_workflow', 'status': {'status': 'connected'}}]}
            if path == '/api/session/active': return 200, {'data': {}}
            if path == '/api/session/lead':
                return 200, {'data': {'id': 'lead', 'agent': 'integration-lead', 'directory': central,
                    'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'author_worktree': repo,
                                 'workflow_gate': True}, 'status': 'idle'}}
            if path.endswith('/inbox'): return 200, {'data': []}
            if path.endswith('/permission'): return 200, {'data': []}
            if '/message?' in path: return 200, {'data': []}
            raise AssertionError((method, path, body))
        return c, wf, req

    def test_workflow_mcp_outage_defers_recovery_without_budget_or_notice(self):
        with tempfile.TemporaryDirectory() as d:
            c,wf,base=self.setup_gate(d)
            def offline(server,method,path,body=None,timeout=15):
                if path.startswith('/api/mcp?'):
                    return 200,{'location':{'directory':c['gate_request']['central_worktree']},
                                'data':[{'name':'ms14_workflow','status':{'status':'disabled'}}]}
                if method=='POST': self.fail('offline dependency must not prompt')
                return base(server,method,path,body,timeout)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice') as notice:
                first=live.reconcile(d,workflow=wf,request=offline,now=1000,pid_alive=lambda _:False)
            health=live.read_health(d,'candidate')
            self.assertEqual(first['pending'],1)
            self.assertEqual(health['state'],'waiting_dependency')
            self.assertEqual(health['recovery_count'],0)
            notice.assert_not_called()
            posts=[]
            def restored(server,method,path,body=None,timeout=15):
                if method=='POST':
                    posts.append(body)
                    return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if '/message?' in path: return 200,{'data':[{'id':'terminal-1','finish':'stop'}]}
                return base(server,method,path,body,timeout)
            live.reconcile(d,workflow=wf,request=restored,now=1001,pid_alive=lambda _:False)
            self.assertEqual(len(posts),1)
            self.assertEqual(live.read_health(d,'candidate')['recovery_count'],1)

    def test_mcp_guard_fails_closed_on_malformed_status_and_preserves_terminal_block(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,base=self.setup_gate(d)
            self.assertFalse(gate_dispatch._workflow_service_ready(None,c['gate_request']['central_worktree'],
                request=lambda *a,**k:(200,{'location':{'directory':c['gate_request']['central_worktree']},
                    'data':[{'name':'ms14_workflow','status':'connected'}]})))
            health=live.read_health(d,'candidate')
            health.update(state='blocked',reason='permanent rejection')
            runtime.writejson(Path(d)/'gate-health'/'candidate.json',health)
            def offline(server,method,path,body=None,timeout=15):
                if path.startswith('/api/mcp?'):
                    return 200,{'location':{'directory':c['gate_request']['central_worktree']},
                        'data':[{'name':'ms14_workflow','status':{'status':'disabled'}}]}
                return base(server,method,path,body,timeout)
            live.reconcile(d,workflow=wf,request=offline,now=1000,pid_alive=lambda _:False)
            saved=live.read_health(d,'candidate')
            self.assertEqual((saved['state'],saved['reason']),('blocked','permanent rejection'))

    def test_registration_binding_is_idempotent_and_preserves_state(self):
        with tempfile.TemporaryDirectory() as d:
            c = {'id': 'candidate', 'snapshot_key': 'snap'}
            row = live.register(d, c, lead_session_id='lead', workflow=runtime)
            row.update(state='recovering', recovery_count=1)
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', row)
            again = live.register(d, c, lead_session_id='lead', workflow=runtime)
            self.assertEqual((again['state'], again['recovery_count']), ('recovering', 1))
            self.assertEqual(live.read_health(d, 'candidate'), again)

    def test_durable_active_job_preempts_blocked_health_and_old_head_filter(self):
        from tooling.agent_workflow import gate_execution
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d)
            newer = dict(c, id='newer', snapshot_key='snap-new', revision_sequence=2, gate_outcome=None)
            runtime.writejson(Path(d) / 'candidates' / 'newer.json', newer)
            job = {'job_id':'candidate','candidate_id':'candidate','snapshot_key':'snap','plan_digest':'plan',
                   'lead_session_id':'lead','origin_session_id':'author','state':'running','worker_identity':{'pid':55,'created':10}}
            gate_execution._write(gate_execution._path(d,'candidate'),job)
            h=live.read_health(d,'candidate'); h.update(state='blocked',reason='stale health',recovery_count=1)
            runtime.writejson(Path(d)/'gate-health'/'candidate.json',h)
            with patch.object(gate_execution,'reconcile',return_value={'pending':0,'active':1,'blocked':0,'errors':[]}),patch.object(gate_execution,'identity_state',return_value='alive'):
                result=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('active worker must preempt lead probing'))
            self.assertEqual(live.read_health(d,'candidate')['state'],'active')
            self.assertEqual(live.read_health(d,'candidate')['recovery_count'],1)
            self.assertEqual(result['active'],1)
            self.assertNotEqual(live.read_health(d,'candidate')['state'],'superseded')

    def test_requested_job_is_queued_and_running_unknown_identity_is_not_active(self):
        from tooling.agent_workflow import gate_execution
        for state,identity,expected in [('requested',None,'queued'),('running',None,'api_unavailable'),('running','alive','active'),('running','dead','queued')]:
            with self.subTest(state=state,identity=identity),tempfile.TemporaryDirectory() as d:
                c,wf,_=self.setup_gate(d)
                job={'job_id':'candidate','candidate_id':'candidate','snapshot_key':'snap','lead_session_id':'lead','state':state,
                     'worker_identity':{'pid':11,'created':2} if identity else None}
                gate_execution._write(gate_execution._path(d,'candidate'),job)
                with patch.object(gate_execution,'reconcile',return_value={}),patch.object(gate_execution,'identity_state',return_value=identity or 'unknown'):
                    result=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('job status must suppress lead-idle recovery'))
                health=live.read_health(d,'candidate')
                self.assertEqual(health['state'],expected)
                self.assertEqual(result['active'],1 if expected=='active' else 0)
                self.assertEqual(health['recovery_count'],0)

    def completed_review_fixture(self,d):
        from tooling.agent_workflow import gate_execution
        c,wf,_=self.setup_gate(d)
        receipt=Path(d)/'receipt.json'; receipt.write_text(json.dumps({'checks':[{'exit_code':0,'evidence':{'status':'PASS'}}]}))
        digest=runtime.sha(receipt); c['gate_plan']={'kind':'readiness'}; c['gate_validation']={'path':str(receipt),'sha256':digest}
        runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
        wf.sha=runtime.sha; wf.verify_gate_receipt=lambda candidate,require_pass: (receipt,json.loads(receipt.read_text()))
        job={'job_id':'candidate','candidate_id':'candidate','snapshot_key':'snap','plan_digest':'plan','lead_session_id':'lead',
             'origin_session_id':'author','state':'completed','receipt':{'path':str(receipt),'sha256':digest}}
        gate_execution._write(gate_execution._path(d,'candidate'),job)
        return c,wf,digest,gate_execution

    def test_completed_receipt_ack_item_is_steered_once_without_false_execution(self):
        with tempfile.TemporaryDirectory() as d:
            c,wf,digest,execution=self.completed_review_fixture(d); posts=[]; patches=[]
            def req(server,method,path,body=None,timeout=15):
                if method=='POST':
                    posts.append(body); return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if path=='/api/session/active': return 200,{'data':{}}
                if path.endswith('/inbox'): return 200,{'data':[{'id':posts[0]['id'],'delivery':'queue'}]}
                if method=='PATCH': patches.append((path,body)); return 204,None
                raise AssertionError((method,path))
            with patch.object(execution,'reconcile',return_value={}):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1000)
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1001)
            delivery=live.read_health(d,'candidate')['execution_review_delivery']
            self.assertEqual(len(posts),1); self.assertEqual(len(patches),1)
            self.assertEqual(delivery['state'],'steered'); self.assertNotIn('execution_observed_at',delivery)
            self.assertEqual(delivery['receipt_sha256'],digest)

    def test_completed_receipt_lead_stop_gets_one_review_only_resume_then_actionable_block(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,digest,execution=self.completed_review_fixture(d); posts=[]; active_calls={'n':0}
            def req(server,method,path,body=None,timeout=15):
                if method=='POST':
                    posts.append(body); return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if path=='/api/session/active':
                    active_calls['n']+=1
                    return 200,{'data':{'lead':{'type':'running'}} if active_calls['n']==1 else {}}
                if path.endswith('/inbox'): return 200,{'data':[]}
                if method=='PATCH': return 204,None
                raise AssertionError((method,path))
            with patch.object(execution,'reconcile',return_value={}),patch.object(gate_dispatch,'deliver_gate_block_notice',return_value={'status':'delivered'}):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1000)
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1001)
                result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1002)
            h=live.read_health(d,'candidate'); delivery=h['execution_review_delivery']
            self.assertEqual(delivery['review_resume_count'],1)
            self.assertEqual(posts[1]['metadata']['receipt_sha256'],digest)
            self.assertEqual(result['blocked'],1); self.assertIn('without a verdict',h['reason'])

    def test_pending_scoped_child_preempts_completed_receipt_idle_classification(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,_,execution=self.completed_review_fixture(d)
            c['scoped_reviews']={'review':{'snapshot_key':'snap','dispatch_pending':True,'session_id':'sid'}}
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            with patch.object(execution,'reconcile',return_value={}),patch.object(gate_dispatch,'_dispatch_scoped_review',return_value={'status':'admitted'}) as child:
                result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('pending scoped child must suppress review-idle logic')))
            child.assert_called_once(); self.assertEqual(result['pending'],1); self.assertEqual(result['blocked'],0)

    def test_completed_receipt_scoped_review_returns_to_the_same_lead(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,digest,execution=self.completed_review_fixture(d)
            wf.gate_lock=runtime.gate_lock; wf.writejson=runtime.writejson; wf.sprint_lock=runtime.sprint_lock
            wf.verify_candidate=lambda candidate:candidate; wf.validid=runtime.validid
            lead_posts=[]
            def req(server,method,path,body=None,timeout=15):
                if method=='POST':
                    lead_posts.append((path,body)); return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if path=='/api/session/active': return 200,{'data':{'lead':{'type':'running'}}}
                raise AssertionError((method,path))
            with patch.object(execution,'reconcile',return_value={}):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1000)
            with patch.object(gate_dispatch,'_register_and_start_observer',return_value=object()):
                reserved=gate_dispatch.scoped_review(type('Args',(),{'id':'candidate','reviewer':'review','actor':'integration-lead',
                    'repo':c['gate_request']['central_worktree']})(),d,workflow=wf)
            self.assertEqual(reserved['status'],'requested')
            with patch.object(execution,'reconcile',return_value={}),patch.object(gate_dispatch,'_dispatch_scoped_review',return_value={'status':'admitted'}):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1001)
            def report_child(args,root,*,workflow):
                fresh=workflow.readjson(Path(root)/'candidates'/'candidate.json')
                rec=fresh['scoped_reviews']['review']; rec.update(dispatch_pending=False,collection_pending=False,
                    report_path='saved-report.json',report_sha256='b'*64)
                workflow.writejson(Path(root)/'candidates'/'candidate.json',fresh)
                return {'status':'reported'}
            with patch.object(execution,'reconcile',return_value={}),patch.object(gate_dispatch,'_dispatch_scoped_review',side_effect=report_child):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1002)
            self.assertEqual(len(lead_posts),2)
            self.assertEqual(lead_posts[0][1]['metadata']['receipt_sha256'],digest)
            self.assertEqual(lead_posts[1][1]['metadata']['report_sha256'],'b'*64)
            self.assertEqual(lead_posts[1][1]['sessionID'] if 'sessionID' in lead_posts[1][1] else lead_posts[1][0],
                             '/api/session/lead/prompt')

    def test_stale_pending_gate_becomes_one_actionable_block_not_forever_pending(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d); c['gate_request'].update(dispatch_pending=True,origin_session_id='author')
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            wf.validid=runtime.validid; wf.gate_lock=runtime.gate_lock; wf.writejson=runtime.writejson; wf.verify_candidate=lambda candidate:candidate
            wf.verify_candidate=lambda candidate: (_ for _ in ()).throw(ValueError('source changed'))
            notice=patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',return_value={'status':'delivered'}); notice_mock=notice.start()
            self.addCleanup(notice.stop)
            with patch.object(gate_dispatch,'_request',side_effect=AssertionError('stale input must not contact service')):
                first=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('no API'))
                second=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('no API'))
            saved=runtime.readjson(Path(d)/'candidates'/'candidate.json')
            self.assertEqual((first['blocked'],second['blocked']),(1,1),(first,second))
            self.assertFalse(saved['gate_request']['dispatch_pending'])
            self.assertIn('source is stale',saved['gate_request']['dispatch_error'])
            notice_mock.assert_called_once()

    def test_invalid_scoped_report_is_delivered_once_to_same_lead_and_stops_collection(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d); c['gate_request']['origin_session_id']='author'
            c['scoped_reviews']={'review':{'session_id':'review-sid','snapshot_key':'snap','prompt_admitted':True,'collection_pending':True}}
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            wf.gate_lock=runtime.gate_lock; wf.writejson=runtime.writejson
            posts=[]
            def req(server,method,path,body=None,timeout=15):
                if method=='POST':
                    posts.append((path,body)); return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if path=='/api/session/active': return 200,{'data':{'lead':{'type':'running'}}}
                raise AssertionError((method,path))
            notice=patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',return_value={'status':'delivered'}); notice_mock=notice.start()
            self.addCleanup(notice.stop)
            with patch.object(gate_dispatch,'_dispatch_scoped_review',side_effect=gate_dispatch.DispatchInputError('report identity/decision mismatch')):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1000)
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req),now=1001)
            saved=runtime.readjson(Path(d)/'candidates'/'candidate.json')['scoped_reviews']['review']
            self.assertFalse(saved['collection_pending']); self.assertIn('report identity',saved['operational_error'])
            self.assertEqual(len(posts),1); self.assertEqual(posts[0][0],'/api/session/lead/prompt')
            self.assertEqual(posts[0][1]['metadata']['workflow_gate_scoped_review_error'],True)
            self.assertEqual(notice_mock.call_count,1)

    def test_transient_scoped_transport_error_remains_pending_for_same_observer_retry(self):
        from tooling.agent_workflow import gate_dispatch
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d); c['scoped_reviews']={'review':{'session_id':'review-sid','snapshot_key':'snap','dispatch_pending':True}}
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c); calls=[]
            def transient(args,root,*,workflow): calls.append(args.reviewer); raise gate_dispatch.DispatchError('service temporarily unavailable')
            with patch.object(gate_dispatch,'_dispatch_scoped_review',side_effect=transient):
                first=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('transient dispatch does not lead-poll')))
                second=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('transient dispatch does not lead-poll')))
            record=runtime.readjson(Path(d)/'candidates'/'candidate.json')['scoped_reviews']['review']
            self.assertEqual(calls,['review','review'],(first,second,live.read_health(d,'candidate')))
            self.assertEqual((first['pending'],second['pending']),(1,1),(first,second))
            self.assertTrue(record['dispatch_pending']); self.assertNotIn('operational_state',record)

    def test_oversized_saved_gate_prompt_terminalizes_once_through_observer(self):
        from tooling.agent_workflow import gate_dispatch, gate_execution, workflow_observer
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            c['gate_outcome']={}
            text='é'*(gate_dispatch.MAX_PROMPT//2+1)
            body={'id':'msg_'+'a'*40,'text':text,'delivery':'queue','resume':True,
                  'metadata':{'workflow_gate_request':True,'candidate_id':'candidate','snapshot_key':'snap'}}
            c['gate_request'].update(dispatch_pending=True,prompt_admitted=False,prompt_body=body,prompt_id=body['id'],
                                     origin_session_id='author',central_worktree=str(Path(d)/'neutral-05'))
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            wf.validid=runtime.validid; wf.gate_lock=runtime.gate_lock; wf.writejson=runtime.writejson; wf.verify_candidate=lambda candidate:candidate
            dispatch=patch.object(gate_dispatch,'_dispatch_gate',wraps=gate_dispatch._dispatch_gate); dispatch_mock=dispatch.start(); self.addCleanup(dispatch.stop)
            register=patch.object(gate_dispatch,'_register_and_start_observer',return_value=object()); register.start(); self.addCleanup(register.stop)
            observer=patch.object(workflow_observer,'ensure_started',return_value=object()); observer.start(); self.addCleanup(observer.stop)
            notice=patch.object(gate_dispatch,'deliver_gate_block_notice',return_value={'status':'delivered'}); notice_mock=notice.start(); self.addCleanup(notice.stop)
            with patch.object(gate_execution,'reconcile',return_value={}):
                first=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('oversized prompt must not contact API'))
                second=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('terminal blocked prompt must not retry API'))
            saved=runtime.readjson(Path(d)/'candidates'/'candidate.json')['gate_request']
            self.assertEqual((first['blocked'],second['blocked']),(1,1),(first,second,live.read_health(d,'candidate')))
            self.assertFalse(saved['dispatch_pending']); self.assertEqual(saved['dispatch_state'],'blocked')
            self.assertIn('prompt limit',saved['dispatch_error']); self.assertEqual(dispatch_mock.call_count,1)
            notice_mock.assert_called_once()

    def test_completed_readiness_receipt_resumes_same_lead_with_saved_prompt(self):
        from tooling.agent_workflow import gate_execution
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            receipt_path=Path(d)/'receipt.json'
            receipt_path.write_text(json.dumps({'checks':[{'exit_code':0,'evidence':{'status':'PASS'}}]}))
            digest=runtime.sha(receipt_path)
            c['gate_plan']={'kind':'readiness'}; c['gate_validation']={'path':str(receipt_path),'sha256':digest}
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            wf.sha=runtime.sha; wf.verify_gate_receipt=lambda candidate,require_pass: (receipt_path,json.loads(receipt_path.read_text()))
            job={'job_id':'candidate','candidate_id':'candidate','snapshot_key':'snap','plan_digest':'plan',
                 'lead_session_id':'lead','origin_session_id':'author','state':'completed','receipt':{'path':str(receipt_path),'sha256':digest}}
            gate_execution._write(gate_execution._path(d,'candidate'),job)
            posts=[]
            def req(server,method,path,body=None,timeout=15):
                if method=='POST':
                    posts.append(body)
                    return 200,{'id':body['id'],'sessionID':'lead','payload':{'text':body['text'],'metadata':body['metadata']}}
                if path=='/api/session/active': return 200,{'data':{}}
                raise AssertionError((method,path))
            with patch.object(gate_execution,'reconcile',return_value={}):
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req))
                live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,req))
            self.assertEqual(len(posts),1)
            self.assertEqual(posts[0]['metadata']['job_id'],'candidate')
            self.assertEqual(posts[0]['metadata']['receipt_sha256'],digest)
            self.assertEqual(posts[0]['metadata']['candidate_id'],'candidate')

    def test_interrupted_job_is_operational_block_and_not_verdict(self):
        from tooling.agent_workflow import gate_execution
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            job={'job_id':'candidate','candidate_id':'candidate','snapshot_key':'snap','plan_digest':'plan',
                 'lead_session_id':'lead','origin_session_id':'author','state':'interrupted',
                 'reason':'worker exited unexpectedly','cleanup_status':'verified_clear'}
            gate_execution._write(gate_execution._path(d,'candidate'),job)
            (Path(d)/'progress').mkdir()
            runtime.writejson(Path(d)/'progress'/'candidate.json',{'candidate_id':'candidate','snapshot_key':'snap',
                'phase':'running','check_index':2,'total':3,'runner_pid':987,'child_pid':988,'log':'check.log','argv':['python','check']})
            notices=[]
            def notice(root,candidate,health,*,workflow,request):
                notices.append(dict(health)); return {'status':'failed','reason':'offline'}
            with patch.object(gate_execution,'reconcile',return_value={}),patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',side_effect=notice):
                result=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('no lead recovery for interrupted job'))
            h=live.read_health(d,'candidate'); progress=runtime.readjson(Path(d)/'progress'/'candidate.json')
            self.assertEqual(result['blocked'],1); self.assertEqual(h['state'],'blocked')
            self.assertIn('worker exited unexpectedly',h['reason']); self.assertIn('verified clear',h['reason'])
            self.assertEqual(len(notices),1); self.assertEqual(progress['phase'],'interrupted')
            self.assertEqual((progress['check_index'],progress['runner_pid'],progress['child_pid']),(2,987,988))
            self.assertIsNone((runtime.readjson(Path(d)/'candidates'/'candidate.json').get('gate_outcome') or {}).get('result'))

    def test_explicit_pending_gate_request_is_dispatched_by_observer_only(self):
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            c['gate_request']['dispatch_pending']=True
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            seen=[]
            def dispatch(args,root,*,workflow):
                seen.append(args.id)
                fresh=runtime.readjson(Path(root)/'candidates'/'candidate.json')
                fresh['gate_request']['prompt_admitted']=True
                runtime.writejson(Path(root)/'candidates'/'candidate.json',fresh)
                return {'status':'admitted'}
            with patch('tooling.agent_workflow.gate_dispatch._dispatch_gate',side_effect=dispatch):
                result=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('dispatch pass must not lead-poll'))
            self.assertEqual(seen,['candidate']); self.assertEqual(result['pending'],1)
            self.assertEqual(live.read_health(d,'candidate')['state'],'observing')

    def test_preadmission_upgrade_preserves_identity_and_rejects_rebinding(self):
        with tempfile.TemporaryDirectory() as d:
            c, _, _ = self.setup_gate(d)
            c['gate_request'].update(session_id=None, prompt_admitted=False)
            runtime.writejson(Path(d) / 'candidates' / 'candidate.json', c)
            (Path(d) / 'gate-health' / 'candidate.json').unlink()
            row = live.register(d, c, workflow=runtime)
            self.assertEqual(row['state'], 'observing')
            c['gate_request'].update(session_id='lead', prompt_admitted=True)
            row = live.register(d, c, lead_session_id='lead', workflow=runtime)
            self.assertEqual((row['session_id'], row['state']), ('lead', 'observing'))
            with self.assertRaisesRegex(ValueError, 'another lead session'):
                live.register(d, c, lead_session_id='different', workflow=runtime)

    def test_successful_same_session_admission_clears_explicit_operational_block(self):
        with tempfile.TemporaryDirectory() as d:
            c, _, _ = self.setup_gate(d)
            h = live.read_health(d, 'candidate'); h.update(state='blocked', reason='temporary admission failure', recovery_count=1)
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', h)
            live.register(d, c, lead_session_id='lead', workflow=runtime)
            self.assertEqual((live.read_health(d, 'candidate')['state'], live.read_health(d, 'candidate')['recovery_count']), ('observing', 1))

    def test_failed_preadmission_is_durably_blocked_without_fabricated_session(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d)
            c['gate_request'].update(session_id=None,prompt_admitted=False,operational_state='blocked',operational_error='service unavailable')
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            (Path(d)/'gate-health'/'candidate.json').unlink()
            live.register(d,c,workflow=runtime)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',return_value={'status':'delivered'}) as notice:
                result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('no lead session exists')))
            self.assertEqual(result['blocked'],1)
            self.assertIsNone(live.read_health(d,'candidate')['session_id'])
            self.assertIn('service unavailable',live.read_health(d,'candidate')['reason'])
            notice.assert_called_once()

    def test_known_session_unadmitted_blocked_progress_is_blocked_and_stale_notice_actionable(self):
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            c['gate_request']['prompt_admitted']=False
            c['gate_request'].pop('operational_error',None); c['gate_request'].pop('operational_state',None)
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            (Path(d)/'gate-health'/'candidate.json').unlink()
            runtime.writejson(Path(d)/'progress'/'candidate.json',{'candidate_id':'candidate','snapshot_key':'snap','phase':'blocked','reason':'old prompt-size error'})
            wf.verify_gate=lambda candidate: (_ for _ in ()).throw(ValueError('source changed'))
            def notice(root,candidate,health,**kwargs):
                self.assertIn('source-stale',health['reason'])
                self.assertIn('fresh revision',health['reason'])
                return {'status':'delivered','id':'notice'}
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',side_effect=notice) as deliver:
                result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('unadmitted prompt must not poll or post')))
            self.assertEqual(result['blocked'],1)
            self.assertEqual(live.read_health(d,'candidate')['state'],'blocked')
            self.assertEqual(live.read_health(d,'candidate')['session_id'],'lead')
            deliver.assert_called_once()

    def test_known_session_unadmitted_request_gets_grace_then_actionable_block(self):
        with tempfile.TemporaryDirectory() as d:
            c,wf,_=self.setup_gate(d)
            c['gate_request']['prompt_admitted']=False
            runtime.writejson(Path(d)/'candidates'/'candidate.json',c)
            h=live.read_health(d,'candidate'); h['registered_at']=1000
            runtime.writejson(Path(d)/'gate-health'/'candidate.json',h)
            result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('no admission means no session API activity')),now=1000)
            self.assertEqual(result['pending'],1)
            self.assertEqual(live.read_health(d,'candidate')['state'],'admitting')
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice',return_value={'status':'delivered'}):
                result=live.reconcile(d,workflow=wf,request=self.with_healthy_mcp(c,lambda *a,**k:self.fail('no retry/post while admission is absent')),now=1060)
            self.assertEqual(result['blocked'],1)
            self.assertIn('never admitted',live.read_health(d,'candidate')['reason'])

    def test_missing_health_directory_bootstraps_only_canonical_current_request(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, req = self.setup_gate(d)
            c['gate_request'].update(session_id=None, prompt_admitted=False)
            runtime.writejson(Path(d) / 'candidates' / 'candidate.json', c)
            (Path(d) / 'gate-health' / 'candidate.json').unlink()
            (Path(d) / 'gate-health').rmdir()
            # A normal pre-admission head is discovered as in-progress, never blocked.
            result = live.reconcile(d, workflow=wf, request=req, now=1000)
            self.assertEqual(result['pending'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'admitting')

    def test_active_endpoint_map_child_chain_is_positive_execution_proof(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                if path == '/api/session/active':
                    return 200, {'data': {'lead-child': {'type': 'running', 'parentID': 'lead'}, 'lead-grandchild': {'type': 'running', 'parentID': 'lead-child'}}}
                if path.endswith('/lead-child'): return 200, {'data': {'id':'lead-child','parentID':'lead'}}
                if path.endswith('/lead-grandchild'): return 200, {'data': {'id':'lead-grandchild','parentID':'lead-child'}}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(result['active'], 1)
            self.assertIn('actively executing', live.read_health(d, 'candidate')['reason'])

    def test_malformed_active_map_is_unknown_not_idle(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                if path == '/api/session/active': return 200, {'data': ['lead']}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req)
            self.assertEqual(result['pending'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'api_unavailable')

    def test_empty_history_idle_lead_blocks_after_grace_and_stays_blocked(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, req = self.setup_gate(d)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                result = live.reconcile(d, workflow=wf, request=req, now=1060)
            self.assertEqual(result['blocked'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'blocked')

    def test_legacy_steer_ack_without_timestamp_gets_one_durable_grace(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d, prompt_id='original')
            health = live.read_health(d, 'candidate'); health.update(recovery_count=1, initial_steer_id='original')
            health.pop('initial_steer_at', None)
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
            item = {'id': 'original', 'delivery': 'steer', 'payload': {'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap'}}}
            def req(server, method, path, body=None, timeout=15):
                if path.endswith('/inbox'): return 200, {'data': [item]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            self.assertEqual(live.read_health(d, 'candidate')['initial_steer_at'], 1000)
            live.reconcile(d, workflow=wf, request=req, now=1059)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                result = live.reconcile(d, workflow=wf, request=req, now=1060)
            self.assertEqual(result['blocked'], 1)

    def test_old_message_changes_never_override_authenticated_idle_endpoint(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'old', 'finish': None, 'time': {'created': 100, 'streamed': 101}, 'content': [{'text': 'a'}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            row['time']['streamed'] = 102
            result = live.reconcile(d, workflow=wf, request=req, now=1001)
            self.assertEqual(result['active'], 0)
            self.assertNotEqual(live.read_health(d, 'candidate')['state'], 'active')

    def test_diagnostic_terminal_retries_existing_completion_without_readiness_publication(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d, outcome={'result': 'diagnostic_complete'})
            c['gate_plan'] = {'kind': 'diagnostic'}
            runtime.writejson(Path(d) / 'candidates' / 'candidate.json', c)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion', return_value={'status': 'delivered'}) as deliver, \
                    patch.object(wf, 'verify_publication', side_effect=AssertionError('diagnostic is not publication'), create=True):
                live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('terminal result must not query session API'))
            deliver.assert_called_once()

    def test_failed_terminal_completion_retries_same_existing_candidate_without_new_session(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, _ = self.setup_gate(d, outcome={'result':'needs_changes'})
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion', side_effect=[{'status':'failed'},{'status':'delivered'}]) as deliver:
                req=lambda *a,**k: self.fail('terminal completion must use its existing receipt path')
                first=live.reconcile(d,workflow=wf,request=req,now=1000)
                self.assertEqual(first['pending'],1); self.assertTrue(first['errors'])
                during_backoff=live.reconcile(d,workflow=wf,request=req,now=1010)
                self.assertEqual(during_backoff['pending'],1); self.assertTrue(during_backoff['errors'])
                self.assertEqual(deliver.call_count,1)
                success=live.reconcile(d,workflow=wf,request=req,now=1030)
                self.assertEqual(success['errors'],[]); self.assertFalse(success['pending'])
            self.assertEqual(deliver.call_count,2)
            self.assertEqual([call.args[0].id for call in deliver.call_args_list],['candidate','candidate'])

    def test_completion_exception_is_visible_until_successful_admission(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, _ = self.setup_gate(d, outcome={'result':'needs_changes'})
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion', side_effect=[OSError('offline'),{'status':'delivered'}]) as deliver:
                failed=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('no API session check'),now=2000)
                self.assertTrue(failed['errors']); self.assertEqual(failed['pending'],1)
                health=live.read_health(d,'candidate')
                self.assertTrue(health['completion_delivery_pending'])
                success=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('no API session check'),now=2030)
                self.assertEqual(success['errors'],[])
                self.assertFalse(live.read_health(d,'candidate')['completion_delivery_pending'])
            self.assertEqual(deliver.call_count,2)

    def test_non_diagnostic_diagnostic_complete_is_not_terminal(self):
        with tempfile.TemporaryDirectory() as d:
            self.setup_gate(d, outcome={'result': 'diagnostic_complete'})
            result = live.reconcile(d, workflow=runtime, request=lambda *a, **k: self.fail('must not trust invalid terminal'))
            self.assertEqual(result['pending'], 1)
            self.assertNotEqual(live.read_health(d, 'candidate')['state'], 'finished')

    def test_historical_terminal_candidate_is_superseded_without_backfill_delivery(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d, outcome={'result': 'ready_for_owner'})
            newer = dict(c, id='newer', snapshot_key='snap-new', revision_sequence=2, gate_outcome=None)
            runtime.writejson(Path(d) / 'candidates' / 'newer.json', newer)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion') as deliver:
                live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('historical terminal must not query API'))
            deliver.assert_not_called()
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'superseded')

    def test_author_completion_steer_is_once_then_observed_without_consumption_claim(self):
        with tempfile.TemporaryDirectory() as d:
            c, _, _ = self.setup_gate(d)
            receipt = {'id': 'completion', 'origin_session_id': 'author', 'status': 'delivered'}
            calls=[]
            def req(server, method, path, body=None, timeout=15):
                calls.append((method, path, body))
                if path == '/api/session/active': return 200, {'data': {}}
                if path.endswith('/inbox'): return 200, {'data': [{'id': 'completion', 'delivery': 'queue'}]}
                if method == 'PATCH': return 200, {'data': {'id': 'completion', 'delivery': 'steer'}}
                raise AssertionError(path)
            h=live.read_health(d,'candidate')
            self.assertIsNone(live._observe_author_delivery(d,c,h,receipt,'author_completion_delivery',req,1000))
            self.assertEqual(sum(x[0]=='PATCH' for x in calls),1)
            def active(*args, **kwargs):
                if args[2] == '/api/session/active': return 200, {'data': {'author': {'type':'running'}}}
                raise AssertionError(args[2])
            self.assertIsNone(live._observe_author_delivery(d,c,h,receipt,'author_completion_delivery',active,1001))
            self.assertEqual(h['author_completion_delivery']['state'],'execution_observed')
            self.assertNotIn('consumed', h['author_completion_delivery'])

    def test_block_notice_receipt_reuses_same_one_steer_then_delivery_block(self):
        with tempfile.TemporaryDirectory() as d:
            c, _, _ = self.setup_gate(d)
            receipt={'id':'notice','origin_session_id':'author','status':'delivered'}
            calls=[]
            def req(server, method, path, body=None, timeout=15):
                calls.append(method)
                if path == '/api/session/active': return 200, {'data': {}}
                if path.endswith('/inbox'): return 200, {'data': [{'id':'notice','delivery':'queue'}]}
                if method == 'PATCH': return 200, {'data': {'id':'notice','delivery':'steer'}}
                raise AssertionError(path)
            h=live.read_health(d,'candidate')
            live._observe_author_delivery(d,c,h,receipt,'author_notice_delivery',req,1000)
            error=live._observe_author_delivery(d,c,h,receipt,'author_notice_delivery',req,1060)
            self.assertIn('one bounded steer',error)
            self.assertEqual(h['author_notice_delivery']['state'],'delivery_blocked')
            self.assertEqual(calls.count('PATCH'),1)

    def test_pending_permission_is_blocked_as_author_action_without_recovery_post(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d)
            calls = []
            def req(server, method, path, body=None, timeout=15):
                calls.append((method, path, body))
                if path.endswith('/permission'): return 200, {'data': [{'id': 'perm'}]}
                if method == 'POST':
                    return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(result['blocked'], 1)
            self.assertFalse(any(x[0] == 'POST' for x in calls))
            health = live.read_health(d, 'candidate')
            self.assertEqual(health['state'], 'blocked')
            self.assertIn('permission', health['reason'])
            self.assertEqual(health['session_id'], 'lead')

    def test_progress_runner_pid_alive_does_not_recover(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, req = self.setup_gate(d)
            self.assertNotEqual(c['worktree']['worktree'], c['gate_request']['central_worktree'])
            runtime.writejson(Path(d) / 'progress' / 'candidate.json', {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'phase': 'queued', 'runner_pid': 77})
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda pid: pid == 77)
            self.assertEqual(result['active'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 0)

    def test_bound_live_runner_is_active_even_when_api_is_down(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, _ = self.setup_gate(d)
            runtime.writejson(Path(d)/'progress'/'candidate.json',{'candidate_id':'candidate','snapshot_key':'snap','phase':'running','runner_pid':77})
            result=live.reconcile(d,workflow=wf,request=lambda *a,**k:self.fail('live bound runner must short-circuit network'),pid_alive=lambda pid:pid==77)
            self.assertEqual(result['active'],1)
            self.assertEqual(result['errors'],[])
            self.assertEqual(live.read_health(d,'candidate')['state'],'active')

    def test_original_queued_item_is_patched_only_once_across_ticks(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d, prompt_id='original')
            item = {'id': 'original', 'delivery': 'queue', 'payload': {'text': 'review', 'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap'}}}
            calls = []
            def req(server, method, path, body=None, timeout=15):
                if path.endswith('/inbox'): return 200, {'data': [item]}
                if method == 'PATCH': calls.append((method, path, body)); return 204, None
                return base(server, method, path, body, timeout)
            for tick in (1, 2, 3): live.reconcile(d, workflow=wf, request=req, now=tick, pid_alive=lambda _: False)
            self.assertEqual(len(calls), 1)
            self.assertEqual(calls[0], ('PATCH', '/api/session/lead/inbox/original', {'delivery': 'steer'}))
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)

    def test_ambiguous_recovery_retries_after_freshness_and_pid_guards(self):
        for stale, alive in ((True, False), (False, True), (True, True)):
            with self.subTest(stale=stale, alive=alive), tempfile.TemporaryDirectory() as d:
                _, wf, base = self.setup_gate(d)
                health = live.read_health(d, 'candidate')
                body = {'id': 'same-message', 'text': 'exact', 'metadata': {'candidate_id': 'candidate'}, 'delivery': 'steer'}
                health.update(recovery_count=1, state='api_unavailable', pending_recovery={'body': body})
                runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
                runtime.writejson(Path(d) / 'progress' / 'candidate.json', {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'phase': 'running', 'runner_pid': 99})
                if stale: wf.verify_gate = lambda c: (_ for _ in ()).throw(ValueError('changed'))
                calls = []
                def req(server, method, path, body=None, timeout=15):
                    if method == 'POST': calls.append(body); return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                    return base(server, method, path, body, timeout)
                with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                    live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: alive)
                self.assertFalse(calls)
                self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)

    def test_tool_calls_without_real_session_idle_timestamp_is_unconfirmed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [{'id': 'tools', 'finish': 'tool-calls', 'time': {'completed': 100}}]}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req, now=1000, pid_alive=lambda _: False)
            self.assertEqual(result['pending'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'observing')

    def test_unchanged_nonterminal_evidence_across_hours_is_unconfirmed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'stream', 'finish': None, 'time': {'created': 100, 'streamed': 101},
                   'content': [{'type': 'tool', 'state': {'status': 'running', 'input': {'step': 'working'}}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            first = live.read_health(d, 'candidate'); stamp = first['last_activity']
            self.assertEqual(first['state'], 'observing')
            live.reconcile(d, workflow=wf, request=req, now=4600)
            second = live.read_health(d, 'candidate')
            self.assertEqual(second['state'], 'blocked')
            self.assertEqual(second['last_activity'], stamp)
            self.assertGreater(second['checked_at'], first['checked_at'])

    def test_streaming_evidence_is_not_execution_proof_without_active_session(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'stream', 'finish': None, 'time': {'created': 100, 'streamed': 101},
                   'content': [{'type': 'tool', 'state': {'status': 'running', 'input': 'a'}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            row['time']['streamed'] = 105; row['content'][0]['state'] = {'status': 'completed', 'output': 'ab'}
            result = live.reconcile(d, workflow=wf, request=req, now=1001)
            health = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 0)
            self.assertEqual(health['last_activity'], 105)
            self.assertEqual(health['state'], 'observing')

    def test_actual_opencode_epoch_millisecond_time_is_normalized_to_seconds(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            created = 1_792_039_025_543
            row = {'id': 'assistant-tool', 'finish': None, 'time': {'created': created},
                   'content': [{'type': 'tool', 'state': {'status': 'running'}, 'time': {'created': created, 'ran': created}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1_792_039_026)
            first = live.read_health(d, 'candidate')
            self.assertAlmostEqual(first['last_activity'], created / 1000)
            self.assertLess(first['last_activity'], 2_000_000_000)
            live.reconcile(d, workflow=wf, request=req, now=1_792_039_030)
            same = live.read_health(d, 'candidate')
            self.assertEqual(same['last_activity'], first['last_activity'])
            row['content'][0]['state']['status'] = 'completed'
            row['content'][0]['time']['completed'] = created + 5_000
            result = live.reconcile(d, workflow=wf, request=req, now=1_792_039_031)
            advanced = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 0)
            self.assertAlmostEqual(advanced['last_activity'], (created + 5_000) / 1000)
            self.assertNotEqual(advanced['state'], 'active')

    def test_untimestamped_content_change_is_observed_not_activity(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'assistant', 'finish': None, 'content': [{'type': 'text', 'text': 'a'}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=100)
            first = live.read_health(d, 'candidate')
            self.assertIsNone(first['last_activity'])
            row['content'][0]['text'] = 'ab'
            result = live.reconcile(d, workflow=wf, request=req, now=200)
            second = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 0)
            self.assertEqual(second['state'], 'blocked')
            self.assertIsNone(second['last_activity'])
            self.assertEqual(second['observed_change_at'], 200.0)

    def test_scheduled_provider_retry_is_unconfirmed_across_hours(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'retry', 'finish': None, 'retry': {'attempt': 1, 'at': 10_000_000, 'error': 'temporary'}, 'time': {'created': 100}}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            one = live.read_health(d, 'candidate')
            live.reconcile(d, workflow=wf, request=req, now=2000)
            two = live.read_health(d, 'candidate')
            self.assertEqual((one['state'], two['state']), ('observing', 'observing'))
            self.assertEqual(two['last_activity'], one.get('last_activity'))
            self.assertEqual(two['retry_at'], 10_000)

    def test_stale_blocks_without_recovery_and_notice_boundary_is_mocked(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, req = self.setup_gate(d)
            wf.verify_gate = lambda c: (_ for _ in ()).throw(ValueError('stale source'))
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}) as notice:
                res = live.reconcile(d, workflow=wf, request=lambda *a, **k: (200, {'data': [{'id': 't', 'finish': 'stop'}]}) if '/message?' in a[2] else req(*a, **k), pid_alive=lambda _: False)
            self.assertEqual(res['blocked'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'blocked')
            notice.assert_called_once()

    def test_original_prompt_uses_patch_of_same_inbox_item(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d, prompt_id='original')
            inbox_item = {'id': 'original', 'delivery': 'queue', 'payload': {'text': 'review', 'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap'}}}
            calls = []
            def req(server, method, path, body=None, timeout=15):
                calls.append((method, path, body))
                if path.endswith('/inbox'): return 200, {'data': [inbox_item]}
                if method == 'PATCH': return 204, None
                return base(server, method, path, body, timeout)
            res = live.reconcile(d, workflow=wf, request=req, now=1000, pid_alive=lambda _: False)
            self.assertEqual(res['recovered'], 1)
            self.assertIn(('PATCH', '/api/session/lead/inbox/original', {'delivery': 'steer'}), calls)
            self.assertFalse(any(x[0] == 'POST' for x in calls))
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                blocked = live.reconcile(d, workflow=wf, request=req, now=1061, pid_alive=lambda _: False)
            self.assertEqual(blocked['blocked'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'blocked')

    def test_wrong_nested_ack_retries_same_id_and_body(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            calls = []
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST':
                    calls.append(body)
                    if len(calls) == 1: return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': {'wrong': True}}}}
                    return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(len(calls), 2)
            self.assertEqual(calls[0], calls[1])

    def test_same_terminal_after_recovery_is_unconfirmed_not_blocked(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            posts=[]
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST':
                    posts.append(body)
                    return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000, pid_alive=lambda _: False)
            result = live.reconcile(d, workflow=wf, request=req, now=1001, pid_alive=lambda _: False)
            self.assertEqual(result['blocked'], 0)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'observing')
            self.assertIn('ms14_workflow_finish',posts[0]['text'])
            self.assertIn('ms14_workflow_status',posts[0]['text'])
            self.assertNotIn('workflow.py',posts[0]['text'])
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                result = live.reconcile(d, workflow=wf, request=req, now=1061, pid_alive=lambda _: False)
            self.assertEqual(result['blocked'], 1)

    def test_different_terminal_after_recovery_blocks_once(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            terminal = ['term-1']
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST': return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': terminal[0], 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            terminal[0] = 'term-2'
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}) as notice:
                self.assertEqual(live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)['blocked'], 1)
                live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            notice.assert_called_once()

    def test_failed_author_notice_retries_until_delivered_then_stops(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d)
            health = live.read_health(d, 'candidate')
            health.update(state='blocked', reason='stopped', author_notice={'status': 'failed', 'id': 'notice-id', 'body': {'text': 'same'}})
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', side_effect=[{'status': 'failed', 'id': 'notice-id'}, {'status': 'delivered', 'id': 'notice-id'}]) as deliver:
                failed=live.reconcile(d, workflow=wf, request=self.with_healthy_mcp(c,lambda *a, **k: self.fail('blocked state must not query lead API')))
                self.assertTrue(failed['errors'])
                h=live.read_health(d,'candidate'); h['notice_retry_at']=0; runtime.writejson(Path(d)/'gate-health'/'candidate.json',h)
                delivered=live.reconcile(d, workflow=wf, request=self.with_healthy_mcp(c,lambda *a, **k: self.fail('blocked state must not query lead API')))
                self.assertFalse(delivered['errors'])
                live.reconcile(d, workflow=wf, request=self.with_healthy_mcp(c,lambda *a, **k: self.fail('delivered notice must not retry')))
            self.assertEqual(deliver.call_count, 2)

    def test_finished_outcome_wins_without_api(self):
        with tempfile.TemporaryDirectory() as d:
            self.setup_gate(d, outcome={'result': 'ready_for_owner'})
            result = live.reconcile(d, workflow=runtime, request=lambda *a, **k: self.fail('unexpected API'))
            self.assertEqual(result['active'] + result['pending'] + result['blocked'], 0)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'finished')

    def test_role_and_location_mismatch_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['directory'] = str(Path(d) / 'wrong')
                return status, response
            result = live.reconcile(d, workflow=wf, request=req)
            self.assertEqual(result['blocked'], 1)
            self.assertIn('central-worktree', live.read_health(d, 'candidate')['reason'])
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 0)

    def test_author_worktree_location_is_not_accepted_for_neutral_lead(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d)
            def author_location(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['directory'] = c['worktree']['worktree']
                return status, response
            result = live.reconcile(d, workflow=wf, request=author_location)
            self.assertEqual(result['blocked'], 1)
            self.assertIn('central-worktree', live.read_health(d, 'candidate')['reason'])

    def test_role_mismatch_and_api_error_do_not_create_verdict(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def wrong_role(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['agent'] = 'author'
                return status, response
            failed = live.reconcile(d, workflow=wf, request=wrong_role)
            self.assertEqual(failed['blocked'], 1)
            result = live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('durable blocked health must not poll lead'))
            self.assertEqual(result['blocked'], 1)
            health = live.read_health(d, 'candidate')
            self.assertEqual(health['recovery_count'], 0)
            self.assertIsNone(runtime.readjson(Path(d) / 'candidates' / 'candidate.json')['gate_outcome'])

    def test_api_error_remains_unknown_not_idle(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d)
            def broken(*args, **kwargs): raise OSError('offline')
            result = live.reconcile(d, workflow=wf, request=self.with_healthy_mcp(c,broken))
            self.assertEqual(result['pending'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'api_unavailable')

    @unittest.skipUnless(os.name == 'nt', 'Windows process handle validation')
    def test_windows_pid_probe_accepts_current_process(self):
        self.assertTrue(live._pid_alive(os.getpid()))


if __name__ == '__main__':
    unittest.main()
