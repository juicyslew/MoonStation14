import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from tooling.agent_workflow import workflow_runtime as runtime

from tooling.agent_workflow import gate_dispatch as d
from tooling.agent_workflow import workflow_observer


class WF:
    calls = []
    fail_verify = False
    validid = staticmethod(lambda value: value)
    git_common = None
    git = staticmethod(lambda repo, *args: str(WF.git_common).encode())
    sha = staticmethod(lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest())
    safe = staticmethod(lambda root, value: (Path(root) / value).resolve())
    evidence_paths = staticmethod(lambda paths, base: [{'png_dimensions': [1, 1]} for _ in paths])
    gate_lock = staticmethod(lambda path: SimpleNamespace(close=lambda: None))
    author_report_dir = staticmethod(lambda c: Path(c['worktree']['worktree']) / 'build' / 'agent-workflow' / c['sprint_id'] / 'reports')
    validate_review_report = staticmethod(runtime.validate_review_report)
    sprint_lock = staticmethod(lambda root,repo,sprint: SimpleNamespace(close=lambda: None))

    @staticmethod
    def readjson(path): return json.loads(Path(path).read_text())
    @staticmethod
    def writejson(path, obj):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        Path(path).write_text(json.dumps(obj))
    @staticmethod
    def verify_candidate(c):
        if WF.fail_verify: raise ValueError('author source changed after API admission')
        return c
    @staticmethod
    def verify_gate(c): WF.calls.append('verify_gate'); return c
    @staticmethod
    def verify_publication(c, root): WF.calls.append('verify_publication')


class GateDispatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=Path.home() / 'AppData/Local/Temp/opencode') if (Path.home() / 'AppData/Local/Temp/opencode').is_dir() else tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.author = self.base / 'MoonStation14-agent-01'
        self.central = self.base / 'MoonStation14-agent-05'
        self.author.mkdir(); self.central.mkdir()
        WF.git_common = self.base / '.git'
        self.root = self.base / 'work' / 'agent_exchange'; (self.root / 'candidates').mkdir(parents=True)
        self.c = {'id': 'candidate-1', 'sprint_id':'fixture-sprint', 'status': 'candidate', 'snapshot_key': 'snap',
                  'worktree': {'worktree': str(self.author)}, 'base_ref': 'main',
                  'gate_plan': {'acceptance_criteria': ['works'], 'fixture': 'test.py', 'checks': []},
                  'evidence': [], 'build_identity': 'unit', 'steps': 'run it', 'agent': 'author'}
        self.path = self.root / 'candidates/candidate-1.json'; WF.writejson(self.path, self.c)
        WF.calls = []
        WF.fail_verify = False
        self.metadata = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap',
                         'origin_session_id': 'author-session', 'author_worktree': str(self.author), 'workflow_gate': True}
        self.observer_calls = []
        observer_patch = patch.object(workflow_observer, 'ensure_started', side_effect=lambda root, workflow=None: self.observer_calls.append((root, workflow)))
        observer_patch.start(); self.addCleanup(observer_patch.stop)

    def args(self, **kwargs):
        values = {'id': 'candidate-1', 'central_worktree': str(self.central), 'origin_session': 'author-session',
                  'server': 'http://opencode.test', 'wait_timeout': 2}
        values.update(kwargs)
        return SimpleNamespace(**values)

    def test_hold_returns_held_before_candidate_or_api_work(self):
        (self.root / 'gate-maintenance.json').write_text(json.dumps({'owner': 'parent', 'note': 'drain', 'allowed_candidate_ids': []}))
        self.path.unlink()
        with patch.object(d, '_request', side_effect=AssertionError('must not call API')):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'held')
        self.assertFalse(self.path.exists())

    def test_new_gate_without_origin_is_rejected_before_any_api(self):
        args=self.args(); args.origin_session=None
        with patch.dict('os.environ',{},clear=True),patch.object(d,'_request',side_effect=AssertionError('no API before origin')):
            result=d.request_gate(args,self.root,workflow=WF)
        self.assertEqual(result['status'],'blocked'); self.assertIn('origin-session',result['detail'])

    def gate_api(self, *, outcome=None, session_agent='integration-lead', directory=None, timeout_on_wait=False):
        state = {'created': 0, 'prompts': 0, 'waits': 0}
        directory = directory or str(self.central)
        def call(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                return 200, {'location': {'directory': str(self.central)}, 'data': {'name': 'integration-lead'}}
            if path == '/api/session' and method == 'POST':
                state['created'] += 1
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path == '/api/session/gate-sid' and method == 'GET':
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path.endswith('/prompt'):
                state['prompts'] += 1
                self.assertEqual(body['delivery'], 'queue'); self.assertIn('acceptance_criteria', body['text'])
                self.assertIn('candidate_record', body['text']); self.assertIn('--asset-workspace', body['text'])
                self.assertRegex(body['id'],r'^msg_[0-9a-f]{40}$')
                self.assertIn('gate-scope-review <candidate-id> --actor integration-lead --reviewer review',body['text'])
                self.assertIn('Read the returned report through the permitted read tool',body['text'])
                self.assertIn('Main build/agent-workflow reports may be read directly with read',body['text'])
                self.assertIn('Reuse its existing session/receipt; do not request repeated/new reviews',body['text'])
                # Background gate work can finish after prompt admission; do not
                # model that completion as an experimental session-wait response.
                if outcome:
                    current = WF.readjson(self.path)
                    current['review'] = {'result': outcome, 'report_sha256': 'report-hash'}
                    current['gate_outcome'] = {'result': outcome, 'report_sha256': 'report-hash', 'findings': ['fix this']}
                    WF.writejson(self.path, current)
                return 200, {'data': {'id': body['id'], 'sessionID': 'gate-sid', 'type': 'user', 'delivery': 'queue',
                    'payload': {'text': body['text'], 'metadata': body['metadata']}}}
            if path.endswith('/wait'):
                state['waits'] += 1
                raise AssertionError('author gate request must not use experimental session wait')
                return 204, None
            raise AssertionError((method, path))
        return state, call

    def test_ready_requires_fresh_core_publication_and_reuses_receipt(self):
        state, api = self.gate_api(outcome='ready_for_owner')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['status'], 'ready_for_owner', first)
        self.assertEqual(second['status'], 'ready_for_owner')
        self.assertEqual((state['created'], state['prompts'], state['waits']), (1, 1, 0))
        self.assertIn('verify_gate', WF.calls); self.assertIn('verify_publication', WF.calls)

    def test_rejection_is_report_bound_and_idempotent(self):
        state, api = self.gate_api(outcome='needs_changes')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            result = d.request_gate(self.args(), self.root, workflow=WF)
            again = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'needs_changes', result); self.assertEqual(again['detail'], ['fix this'])
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_wait_timeout_preserves_session_without_duplicate_prompt(self):
        state, api = self.gate_api(timeout_on_wait=True)
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['session_id'], 'gate-sid'); self.assertEqual(first['status'], 'pending')
        self.assertEqual(second['session_id'], 'gate-sid')
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_admitted_retry_preserves_runner_progress_and_registration(self):
        state, api = self.gate_api()
        c = dict(self.c, gate_request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
            'origin_session_id':'author-session','prompt_admitted':True,'prompt_id':'already-admitted'})
        WF.writejson(self.path,c)
        progress = self.root/'progress/candidate-1.json'; progress.parent.mkdir(parents=True)
        progress.write_text(json.dumps({'candidate_id':'candidate-1','snapshot_key':'snap','phase':'running','runner_pid':456}))
        healthdir=self.root/'gate-health'; healthdir.mkdir()
        hp=healthdir/'candidate-1.json'; hp.write_text(json.dumps({'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid','state':'observing','registered_at':1,'recovery_count':4}))
        with patch.object(d,'_request',side_effect=api),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'pending'); self.assertEqual((state['created'],state['prompts']),(0,0))
        self.assertEqual(json.loads(progress.read_text())['runner_pid'],456)
        self.assertEqual(json.loads(hp.read_text())['recovery_count'],4)

    def test_uncertain_admission_retries_same_prompt_id_and_body(self):
        state, api = self.gate_api()
        prompts=[]; fail={'once':True}
        def uncertain(*args,**kwargs):
            if args[2].endswith('/prompt'):
                prompts.append(args[3])
                if fail['once']:
                    fail['once']=False
                    raise d.DispatchError('ambiguous send')
            return api(*args,**kwargs)
        with patch.object(d,'_request',side_effect=uncertain),patch.object(d,'_wait_for_candidate_change',return_value=False):
            first=d.request_gate(self.args(),self.root,workflow=WF)
            second=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(first['status'],'blocked')
        self.assertEqual(second['status'],'pending')
        self.assertEqual(prompts[0],prompts[1]); self.assertEqual(state['created'],1)

    def test_matching_blocked_health_returns_without_verdict(self):
        state, api = self.gate_api()
        original=__import__('tooling.agent_workflow.gate_liveness',fromlist=['register']).register
        def register(*args,**kwargs):
            h=original(*args,**kwargs); h.update(state='blocked',reason='lead exited')
            (self.root/'gate-health/candidate-1.json').write_text(json.dumps(h)); return h
        with patch.object(d,'_request',side_effect=api),patch('tooling.agent_workflow.gate_liveness.register',side_effect=register):
            result=d.request_gate(self.args(wait_timeout=10),self.root,workflow=WF)
        self.assertEqual(result['status'],'blocked'); self.assertIn('lead exited',result['detail'])

    def test_wrong_health_binding_is_ignored_and_expiry_preserves_runner(self):
        state, api = self.gate_api()
        original=__import__('tooling.agent_workflow.gate_liveness',fromlist=['register']).register
        def register(*args,**kwargs):
            h=original(*args,**kwargs); h.update(state='blocked',session_id='other-session')
            (self.root/'gate-health/candidate-1.json').write_text(json.dumps(h)); return h
        with patch.object(d,'_request',side_effect=api),patch('tooling.agent_workflow.gate_liveness.register',side_effect=register),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'pending')
        progress=json.loads((self.root/'progress/candidate-1.json').read_text())
        self.assertNotEqual(progress['phase'],'pending')

    def test_observer_bootstrap_occurs_after_dispatch_lock_release(self):
        state, api = self.gate_api(outcome='ready_for_owner')
        flags={'released':False}
        class Lock:
            def close(self): flags['released']=True
        with patch.object(WF,'gate_lock',side_effect=lambda path: Lock()),patch.object(d,'_request',side_effect=api):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'ready_for_owner')
        self.assertTrue(flags['released']); self.assertTrue(self.observer_calls)

    def test_new_admission_records_distinct_admitted_progress_and_durable_hook_order(self):
        state, api = self.gate_api()
        events=[]
        class Lock:
            def close(self): events.append('released')
        register=__import__('tooling.agent_workflow.gate_liveness',fromlist=['register']).register
        def reg(root,candidate,*,lead_session_id,workflow=None):
            events.append(('registered',WF.readjson(self.path)['gate_request'].get('prompt_admitted')))
            return register(root,candidate,lead_session_id=lead_session_id,workflow=workflow)
        def observer(root,workflow=None):
            events.append(('observer',events[-1]))
        with patch.object(WF,'gate_lock',side_effect=lambda path: Lock()),patch.object(d,'_request',side_effect=api), \
             patch('tooling.agent_workflow.gate_liveness.register',side_effect=reg),patch.object(workflow_observer,'ensure_started',side_effect=observer), \
             patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'pending')
        self.assertEqual(events[0],'released')
        self.assertEqual(events[1],('registered',True))
        self.assertEqual(events[3][0],'observer')
        self.assertEqual(json.loads((self.root/'progress/candidate-1.json').read_text())['phase'],'admitted')

    def test_wrong_session_and_missing_persisted_outcome_block(self):
        state, api = self.gate_api(session_agent='review')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked'); self.assertIn('unexpected role', result['detail'])
        state, api = self.gate_api()
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'pending', result); self.assertIn('No persisted verdict', result['detail'])

    def test_non_git_central_directory_is_accepted(self):
        # These fixture directories intentionally have no .git; dispatch must not
        # impose a shared git-common-dir requirement on the coordinator.
        self.assertFalse((self.central / '.git').exists())
        state, api = self.gate_api(outcome='ready_for_owner')
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'ready_for_owner', result)
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_author_completion_uses_deterministic_id_and_acknowledged_payload(self):
        self.c['agent']='architecture-lead'
        self.c.update(gate_request={'origin_session_id':'author-session'},
                      review={'result':'needs_changes','report_sha256':'rh'},
                      gate_outcome={'result':'needs_changes','report_sha256':'rh','findings':['fix the first issue', 'Second finding ' + ('preserve all details ' * 30)]})
        WF.writejson(self.path,self.c)
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path=='/api/session/author-session':
                return 200,{'data':{'id':'author-session','agent':'agent-consultant','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                WF.fail_verify=True
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user',
                    'payload':{'text':body['text'],'metadata':body['metadata']},'delivery':'steer'}}
            raise AssertionError((method,path))
        with patch.object(d,'_request',side_effect=api):
            first=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
            self.assertEqual(first['status'],'delivered')
            self.assertEqual(WF.readjson(self.path)['author_completion']['status'],'delivered')
            WF.fail_verify=False
            second=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        self.assertEqual(first['status'],'delivered'); self.assertEqual(second['status'],'delivered')
        self.assertEqual(len(posted),1); self.assertEqual(posted[0]['delivery'],'steer'); self.assertTrue(posted[0]['resume'])
        self.assertEqual(posted[0]['id'], 'msg_'+hashlib.sha256('candidate-1\0snap\0needs_changes\0rh\0author-session'.encode()).hexdigest()[:40])
        saved=WF.readjson(self.path)['author_completion']
        self.assertEqual(saved['status'],'delivered')
        self.assertEqual(saved['delivery_semantics'],'API acknowledged; model consumption not implied')
        self.assertIn('Bound report:',posted[0]['text']); self.assertIn('1. fix the first issue',posted[0]['text'])
        self.assertIn('2. Second finding',posted[0]['text']); self.assertIn('neither starts new checks',posted[0]['text'])
        self.assertIn('workflow.py status --candidate candidate-1',posted[0]['text'])
        WF.fail_verify=False

    def test_nested_payload_mismatch_is_not_acknowledged(self):
        self.c.update(gate_request={'origin_session_id':'author-session'},review={'result':'needs_changes','report_sha256':'rh'},gate_outcome={'result':'needs_changes','report_sha256':'rh','findings':['fix']})
        WF.writejson(self.path,self.c)
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.endswith('/author-session'): return 200,{'data':{'agent':'author','directory':str(self.author)}}
            if path.endswith('/prompt'):
                payload={'text':body['text'],'metadata':dict(body['metadata'],result='ready_for_owner')}
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','payload':payload,'delivery':'steer'}}
            raise AssertionError((method,path))
        with patch.object(d,'_request',side_effect=api):
            result=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'failed')
        self.assertIn('not acknowledge',result['reason'])
        self.assertEqual(WF.readjson(self.path)['author_completion']['status'],'failed')

    def test_operational_block_notice_is_candidate_bound_and_nested_acknowledged(self):
        self.c.update(gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap'},
                      worktree={'worktree':str(self.author)}, agent='architecture-lead')
        WF.writejson(self.path,self.c)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid',
                'state':'blocked','reason':'lead session stopped'}
        WF.fail_verify=True  # Source staleness must not suppress this historical operational notice.
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path == '/api/info': return 200,{'version':'2.0.18'}
            if path.endswith('/author-session'): return 200,{'data':{'id':'author-session','agent':'orchestrate-ms14','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError(path)
        with patch.object(d,'_request',side_effect=api):
            result=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)
            again=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)
        self.assertEqual(result['status'],'delivered'); self.assertEqual(again['id'],result['id'])
        self.assertEqual(len(posted),1); self.assertTrue(posted[0]['metadata']['workflow_gate_blocked'])
        self.assertNotIn('author_notice',WF.readjson(self.path))
        self.assertEqual(json.loads((self.root/'gate-health/candidate-1.json').read_text())['author_notice']['status'],'delivered')
        WF.fail_verify=False

    def test_block_notice_rejects_wrong_origin_session_without_posting(self):
        self.c.update(gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap'},
                      worktree={'worktree':str(self.author)}, agent='author')
        WF.writejson(self.path,self.c)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'other-session','state':'blocked'}
        with patch.object(d,'_request',side_effect=AssertionError('must reject before network')):
            with self.assertRaisesRegex(d.DispatchError,'binding mismatch'):
                d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)

    def test_author_origin_rejects_integration_lead_and_wrong_root(self):
        for profile,directory in [('integration-lead',str(self.author)),('agent-consultant',str(self.central))]:
            with self.subTest(profile=profile,directory=directory):
                with self.assertRaisesRegex(d.DispatchError,'not an author session'):
                    d._author_origin('server',self.c,'author-session',request=lambda *args,**kwargs:(200,{'data':{
                        'id':'author-session','agent':profile,'directory':directory}}))

    def test_block_notice_reuses_ambiguous_body_and_never_rewrites_concurrent_verdict(self):
        self.c.update(gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap'},
                      worktree={'worktree':str(self.author)},agent='author')
        WF.writejson(self.path,self.c)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid','state':'blocked','reason':'first reason'}
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path.endswith('/author-session'): return 200,{'data':{'agent':'author','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                if len(posted)==1: return 200,{'data':[]}
                current=WF.readjson(self.path); current['gate_outcome']={'result':'ready_for_owner'}; WF.writejson(self.path,current)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError(path)
        with patch.object(d,'_check_v2'),patch.object(d,'_server',return_value='server'):
            first=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF,request=api)
            health['reason']='changed reason'
            second=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF,request=api)
        self.assertEqual(first['status'],'failed'); self.assertEqual(second['status'],'delivered')
        self.assertEqual(posted[0],posted[1]); self.assertEqual(WF.readjson(self.path)['gate_outcome']['result'],'ready_for_owner')

    def test_scoped_review_rejects_wrong_central_actor_path_before_service(self):
        args = SimpleNamespace(id='candidate-1', reviewer='review', actor='integration-lead',
                               central_worktree=str(self.central), repo=str(self.base / 'not-central'))
        with patch.object(d, '_server', side_effect=AssertionError('service used before actor check')):
            with self.assertRaisesRegex(d.DispatchError, 'must be invoked from the central integration worktree'):
                d.scoped_review(args, self.root, workflow=WF)

    def test_scoped_visual_review_uses_candidate_root_and_copies_bound_image(self):
        image = self.author / 'evidence.png'
        image.write_bytes(b'\x89PNG\r\n\x1a\n' + b'\0' * 16)
        self.c['visual_review_required'] = True
        self.c['evidence'] = [{'path': str(image), 'sha256': WF.sha(image)}]
        WF.writejson(self.path, self.c)
        report = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'fidelity-reviewer',
                  'decision': 'ready_for_owner', 'findings': ['matches'],
                  'viewed_images': [{'path': str(image), 'sha256': WF.sha(image)}]}
        state = {'created': 0, 'body': None, 'location': None}
        def api(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                self.assertIn('location%5Bdirectory%5D=', path)
                return 200, {'location': {'directory': str(self.author)}, 'data': {'name': 'fidelity-reviewer'}}
            if path == '/api/session' and method == 'POST':
                state['created'] += 1; state['location'] = body['location']['directory']
                return 200, {'data': {'id': 'review-sid', 'agent': 'fidelity-reviewer', 'directory': str(self.author)}}
            if path == '/api/session/review-sid' and method == 'GET':
                return 200, {'data': {'id': 'review-sid', 'agent': 'fidelity-reviewer', 'directory': str(self.author)}}
            if path.endswith('/prompt'):
                state['body'] = body
                self.assertIn('acceptance_criteria', body['text']); return 200, {'data': []}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'fidelity-reviewer', 'finish': 'stop',
                    'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError((method, path))
        with patch.object(d, '_request', side_effect=api):
            result = d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='fidelity-reviewer',
                actor='integration-lead', server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)
        self.assertEqual(state['location'], str(self.author)); self.assertEqual(result['status'], 'reported')
        self.assertIn("reviewer field MUST be exactly 'fidelity-reviewer'", state['body']['text'])
        brief = json.loads(state['body']['text'].split('\n', 1)[1])
        schema = brief['report_schema']
        self.assertEqual(schema['reviewer'], 'fidelity-reviewer')
        self.assertEqual(schema['decision']['enum'], ['needs_changes', 'ready_for_owner'])
        self.assertEqual(schema['findings']['minItems'], 1)
        self.assertEqual(schema['findings']['maxItems'], 100)
        self.assertEqual(brief['implementation_steps'], 'run it')
        self.assertIn('gate_validation', brief)
        saved = WF.readjson(result['report'])
        binding = saved['original_images'][0]
        self.assertEqual(binding['original_sha256'], WF.sha(image))
        self.assertEqual(WF.sha(Path(result['report']).parent / binding['path']), WF.sha(image))
        image.write_bytes(b'changed')
        with patch.object(d, '_request', side_effect=api):
            with self.assertRaises(d.DispatchError):
                d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='fidelity-reviewer',
                    actor='integration-lead', server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)

    def test_scoped_review_selects_only_completed_final_json(self):
        report = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'review',
                  'decision': 'needs_changes', 'findings': ['fix'], 'viewed_images': []}
        def api(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                return 200, {'location': {'directory': str(self.author)}, 'data': {'name': 'review'}}
            if path == '/api/session' and method == 'POST':
                self.assertEqual(body['location']['directory'], str(self.author))
                return 200, {'data': {'id': 'sid', 'agent': 'review', 'directory': str(self.author)}}
            if path == '/api/session/sid' and method == 'GET':
                return 200, {'data': {'id': 'sid', 'agent': 'review', 'directory': str(self.author)}}
            if path.endswith('/prompt'): return 200, {'data': []}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'review', 'finish': None, 'content': [{'type': 'text', 'text': json.dumps(report)}]},
                                        {'agent': 'review', 'finish': 'stop', 'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError(path)
        with patch.object(d, '_request', side_effect=api):
            result = d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='review', actor='integration-lead',
                server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)
        self.assertEqual(WF.readjson(result['report'])['decision'], 'needs_changes')

    def test_authenticated_default_cli_uses_existing_background_service(self):
        response = SimpleNamespace(returncode=0, stdout='{"version":"2.0.18","finding":"review → fix — preserve UTF-8"}', stderr='')
        with patch.object(d.subprocess, 'run', return_value=response) as run:
            status, info = d._request(None, 'GET', '/api/info')
        argv = run.call_args.args[0]
        self.assertEqual((status, info['version']), (200, '2.0.18'))
        self.assertEqual(info['finding'], 'review → fix — preserve UTF-8')
        self.assertEqual(argv, ['opencode', 'api', 'GET', '/api/info'])
        self.assertEqual(run.call_args.kwargs['encoding'], 'utf-8')
        self.assertNotIn('--server', argv); self.assertNotIn('--standalone', argv)

    def test_explicit_server_and_json_payload_use_authenticated_cli(self):
        response = SimpleNamespace(returncode=0, stdout='{"data":{"id":"s"}}', stderr='')
        payload = {'agent': 'integration-lead', 'location': {'directory': 'C:/central'}}
        with patch.object(d.subprocess, 'run', return_value=response) as run:
            _, result = d._request('http://127.0.0.1:49374', 'POST', '/api/session', payload)
        argv = run.call_args.args[0]
        self.assertEqual(argv[:5], ['opencode', 'api', '--server', 'http://127.0.0.1:49374', 'POST'])
        self.assertEqual(json.loads(argv[-1]), payload)
        self.assertEqual(result['data']['id'], 's')
        self.assertEqual(run.call_args.kwargs['encoding'], 'utf-8')

    def test_invalid_utf8_cli_response_is_a_dispatch_error(self):
        with patch.object(d.subprocess, 'run', side_effect=UnicodeDecodeError('utf-8', b'\xff', 0, 1, 'invalid')):
            with self.assertRaisesRegex(d.DispatchError, 'invalid'):
                d._request(None, 'GET', '/api/info')

    def test_cli_errors_invalid_json_and_oversized_responses_block(self):
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=1, stdout='', stderr='401 Unauthorized')):
            with self.assertRaisesRegex(d.DispatchError, '401 Unauthorized'):
                d._request(None, 'GET', '/api/info')
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout='not json', stderr='')):
            with self.assertRaisesRegex(d.DispatchError, 'invalid JSON'):
                d._request(None, 'GET', '/api/info')
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout='x' * (d.MAX_REPLY + 1), stderr='')):
            with self.assertRaisesRegex(d.DispatchError, 'exceeds limit'):
                d._request(None, 'GET', '/api/info')

    def test_bounded_large_user_history_page_is_accepted(self):
        raw=json.dumps({'data':[{'type':'user','payload':{'text':'x'*100_000}}]})
        with patch.object(d.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=raw,stderr='')):
            status,response=d._request(None,'GET','/api/session/session/messages')
        self.assertEqual(status,200); self.assertEqual(len(response['data'][0]['payload']['text']),100_000)

    def test_review_contract_rejects_provider_name_pass_and_empty_findings(self):
        candidate = {'id': 'candidate-1', 'snapshot_key': 'snap'}
        valid = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'review',
                 'decision': 'ready_for_owner', 'findings': ['Reviewed current source and validation evidence.'],
                 'viewed_images': []}
        self.assertIs(runtime.validate_review_report(valid, candidate, 'review'), valid)
        for changes in ({'reviewer': 'GPT-6 Luna'}, {'decision': 'PASS'}, {'findings': []},
                        {'findings': ['']}, {'findings': [' \t\n ']}):
            invalid = dict(valid); invalid.update(changes)
            with self.assertRaises(ValueError):
                runtime.validate_review_report(invalid, candidate, 'review')


if __name__ == '__main__': unittest.main()
