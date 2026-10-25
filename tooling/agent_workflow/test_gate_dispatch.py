import hashlib
import importlib
import json
import os
import sys
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
    @staticmethod
    def verify_gate_receipt(c, *, require_pass):
        ref=c.get('gate_validation',{}); path=Path(ref.get('path',''))
        if not path.is_file() or WF.sha(path)!=ref.get('sha256'): raise ValueError('gate validation receipt missing or stale')
        return path,WF.readjson(path)


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
        observer_patch = patch.object(workflow_observer, 'ensure_started', side_effect=lambda root, workflow=None: self.observer_calls.append((root, workflow)) or object())
        observer_patch.start(); self.addCleanup(observer_patch.stop)
        # The dispatcher uses installed-layout bare imports when the tooling
        # directory is on sys.path. Bind those names to the same patched owners.
        observer_modules_patch=patch.dict(sys.modules,{'workflow_observer':workflow_observer})
        observer_modules_patch.start(); self.addCleanup(observer_modules_patch.stop)
        self.liveness_calls=[]
        def register(root,candidate,*,lead_session_id,workflow=None):
            self.liveness_calls.append(lead_session_id)
            hp=Path(root)/'gate-health'/(candidate['id']+'.json')
            health=WF.readjson(hp) if hp.is_file() else {}
            if health.get('session_id') != lead_session_id: health={}
            health.update(candidate_id=candidate['id'],snapshot_key=candidate['snapshot_key'],
                          session_id=lead_session_id,state='observing',registered_at=health.get('registered_at',1),
                          recovery_count=health.get('recovery_count',0))
            WF.writejson(hp,health)
            return health
        fake_liveness=SimpleNamespace(register=register,
            read_health=lambda root,ident: WF.readjson(Path(root)/'gate-health'/(ident+'.json')) if (Path(root)/'gate-health'/(ident+'.json')).is_file() else None,
            _write=lambda path,value: WF.writejson(path,value))
        self.fake_liveness=fake_liveness
        # gate_dispatch intentionally imports the installed-layout bare module when
        # running from tooling/agent_workflow, while package imports use the
        # fully-qualified name. Both must resolve to this fixture fake.
        modules_patch=patch.dict(sys.modules,{
            'gate_liveness':fake_liveness,
            'tooling.agent_workflow.gate_liveness':fake_liveness,
        })
        modules_patch.start(); self.addCleanup(modules_patch.stop)
        package=importlib.import_module('tooling.agent_workflow')
        missing=object()
        prior_attribute=getattr(package,'gate_liveness',missing)
        package.gate_liveness=fake_liveness
        def restore_package_attribute():
            if prior_attribute is missing:
                delattr(package,'gate_liveness')
            else:
                package.gate_liveness=prior_attribute
        self.addCleanup(restore_package_attribute)
        liveness_patch=patch.object(fake_liveness,'register',side_effect=register)
        liveness_patch.start(); self.addCleanup(liveness_patch.stop)
        self.public_request_gate=d.request_gate
        self.public_scoped_review=d.scoped_review
        public_patch=patch.object(d,'request_gate',d._dispatch_gate); public_patch.start(); self.addCleanup(public_patch.stop)
        scoped_patch=patch.object(d,'scoped_review',d._dispatch_scoped_review); scoped_patch.start(); self.addCleanup(scoped_patch.stop)

    def args(self, **kwargs):
        values = {'id': 'candidate-1', 'central_worktree': str(self.central), 'origin_session': 'author-session',
                  'server': 'http://opencode.test', 'wait_timeout': 2}
        values.update(kwargs)
        return SimpleNamespace(**values)

    def test_expected_not_found_requires_opt_in_and_exact_cli_error(self):
        payload=json.dumps({'_tag':'SessionNotFoundError','sessionID':'missing',
                            'message':'Session not found: missing'})
        def result(code=1, stderr='HTTP 404 Not Found\n', stdout=payload):
            return SimpleNamespace(returncode=code,stderr=stderr,stdout=stdout)
        with patch.object(d.subprocess,'run',return_value=result()):
            with self.assertRaises(d.DispatchError): d._request(None,'GET','/api/session/missing')
            self.assertEqual(d._request(None,'GET','/api/session/missing',accepted_http_statuses=(404,)),(404,None))
        for code,stderr,stdout,path,method in (
                (2,'HTTP 404 Not Found',payload,'/api/session/missing','GET'),
                (1,'HTTP 403 Forbidden',payload,'/api/session/missing','GET'),
                (1,'body says HTTP 404 Not Found',payload,'/api/session/missing','GET'),
                (1,'HTTP 404 Not Found','{}','/api/session/missing','GET'),
                (1,'HTTP 404 Not Found',json.dumps({'_tag':'SessionNotFoundError','sessionID':'other'}),'/api/session/missing','GET'),
                (1,'HTTP 404 Not Found',json.dumps({'_tag':'OtherError','sessionID':'missing'}),'/api/session/missing','GET'),
                (1,'HTTP 404 Not Found',payload,'/api/session/missing/inbox','GET'),
                (1,'HTTP 404 Not Found',payload,'/api/session/missing','POST')):
            with self.subTest(code=code,stderr=stderr,stdout=stdout,path=path,method=method),patch.object(d.subprocess,'run',return_value=result(code,stderr,stdout)):
                with self.assertRaises(d.DispatchError):
                    d._request(None,method,path,accepted_http_statuses=(404,))
        with self.assertRaises(d.DispatchError):
            d._request(None,'GET','/x',accepted_http_statuses=(403,))

    def test_public_gate_intake_is_durable_without_source_verification_or_service_io(self):
        self.intake_stubs()
        with patch.object(d,'_candidate',side_effect=AssertionError('public intake must not hash/Git verify')), \
             patch.object(d,'_server',side_effect=AssertionError('public intake must not contact service')):
            result=self.public_request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'requested')
        gate=WF.readjson(self.path)['gate_request']
        self.assertTrue(gate['dispatch_pending']); self.assertEqual(gate['origin_session_id'],'author-session')

    def test_expired_submit_deadline_cannot_write_gate_intent(self):
        self.intake_stubs()
        candidate=WF.readjson(self.path)
        self.assertNotIn('gate_request',candidate)
        with patch.object(WF,'check_operation_deadline',side_effect=RuntimeError('expired'),create=True):
            with self.assertRaisesRegex(RuntimeError,'expired'):
                self.public_request_gate(self.args(),self.root,workflow=WF)
        self.assertNotIn('gate_request',WF.readjson(self.path))

    def test_public_scoped_review_only_reserves_for_observer(self):
        self.intake_stubs()
        candidate=WF.readjson(self.path); candidate['gate_request']={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}; WF.writejson(self.path,candidate)
        with patch.object(d,'_candidate',side_effect=AssertionError('public scope intake must not Git verify')), \
             patch.object(d,'_server',side_effect=AssertionError('public scope intake must not contact service')):
            result=self.public_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.central)),self.root,workflow=WF)
        self.assertEqual(result['status'],'requested')
        record=WF.readjson(self.path)['scoped_reviews']['review']
        self.assertTrue(record['dispatch_pending']); self.assertFalse(record['prompt_admitted'])

    def test_finalized_scoped_review_rejects_new_request_without_mutation_or_observer(self):
        candidate=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)},
                       gate_outcome={'result':'ready_for_owner'})
        WF.writejson(self.path,candidate)
        before=self.path.read_bytes()
        with self.assertRaisesRegex(d.DispatchError,'gate finalized; inspect status, no new reviews'):
            self.public_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.central)),self.root,workflow=WF)
        self.assertEqual(self.path.read_bytes(),before)
        self.assertFalse(self.observer_calls)

    def test_scoped_review_takes_shared_sprint_lock_before_dispatch_lock(self):
        self.intake_stubs()
        WF.writejson(self.path,dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap',
            'prompt_admitted':True,'central_worktree':str(self.central)}))
        events=[]
        class Lock:
            def close(self): pass
        with patch.object(WF,'sprint_lock',side_effect=lambda *a: events.append('sprint') or Lock()), \
             patch.object(WF,'gate_lock',side_effect=lambda *a: events.append('dispatch') or Lock()):
            self.public_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.central)),self.root,workflow=WF)
        self.assertEqual(events[:2],['sprint','dispatch'])

    def test_finalized_collected_review_retry_returns_record_without_observer(self):
        report=self.author/'saved-review.json'
        report.write_text(json.dumps({'candidate_id':'candidate-1','snapshot_key':'snap','reviewer':'review',
            'decision':'ready_for_owner','findings':['checked'],'viewed_images':[]}),encoding='utf-8')
        digest=WF.sha(report)
        candidate=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)},
            gate_outcome={'result':'ready_for_owner'},scoped_reviews={'review':{'snapshot_key':'snap','session_id':'review-sid',
                'prompt_admitted':True,'report_path':str(report),'report_sha256':digest,'collection_pending':False}})
        WF.writejson(self.path,candidate)
        before=self.path.read_bytes()
        result=self.public_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.central)),self.root,workflow=WF)
        self.assertEqual(result['status'],'reported'); self.assertIn('freshness not checked',result['detail'])
        self.assertEqual(self.path.read_bytes(),before); self.assertFalse(self.observer_calls)

    def test_terminal_review_rejects_pending_or_corrupt_collected_record(self):
        report=self.author/'report.json'; report.write_text(json.dumps({'candidate_id':'candidate-1','snapshot_key':'snap',
            'reviewer':'review','decision':'ready_for_owner','findings':['checked']}),encoding='utf-8')
        for record in ({'snapshot_key':'snap','session_id':'sid','prompt_admitted':True,'report_path':str(report),
                        'report_sha256':WF.sha(report),'collection_pending':True},
                       {'snapshot_key':'snap','session_id':'sid','prompt_admitted':True,'report_path':str(report),
                        'report_sha256':'wrong'}):
            with self.subTest(record=record):
                candidate=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)},
                    owner_decision={'decision':'accept'},scoped_reviews={'review':record})
                WF.writejson(self.path,candidate); before=self.path.read_bytes()
                with self.assertRaisesRegex(d.DispatchError,'gate finalized'):
                    self.public_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.central)),self.root,workflow=WF)
                self.assertEqual(self.path.read_bytes(),before)

    def test_public_gate_retry_uses_recorded_origin_and_terminal_is_not_fresh_ready(self):
        candidate=dict(self.c,gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap',
            'prompt_admitted':True},gate_outcome={'result':'ready_for_owner'})
        WF.writejson(self.path,candidate); self.intake_stubs()
        with patch.dict(os.environ,{'OPENCODE_SESSION_ID':''},clear=False),patch.object(d,'_candidate',side_effect=AssertionError('no Git')),patch.object(d,'_server',side_effect=AssertionError('no API')):
            result=self.public_request_gate(SimpleNamespace(id='candidate-1'),self.root,workflow=WF)
        self.assertEqual(result['status'],'recorded'); self.assertEqual(result['recorded_outcome'],'ready_for_owner')
        self.assertEqual(result['freshness'],'not_checked')
        with self.assertRaisesRegex(d.DispatchError,'another author origin'):
            self.public_request_gate(SimpleNamespace(id='candidate-1',origin_session='other'),self.root,workflow=WF)

    def test_public_scoped_review_rejects_wrong_root_and_unbound_lead_before_writes(self):
        args=SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',repo=str(self.author))
        with self.assertRaisesRegex(d.DispatchError,'central integration worktree'):
            self.public_scoped_review(args,self.root,workflow=WF)
        self.assertNotIn('scoped_reviews',WF.readjson(self.path))
        candidate=WF.readjson(self.path); candidate['gate_request']={'session_id':'lead','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}; WF.writejson(self.path,candidate)
        args.repo=str(self.central)
        with patch.dict(os.environ,{'OPENCODE_SESSION_ID':'other'}):
            with self.assertRaisesRegex(d.DispatchError,'not the recorded'):
                self.public_scoped_review(args,self.root,workflow=WF)
        self.assertNotIn('scoped_reviews',WF.readjson(self.path))

    def intake_stubs(self, *, observer_error=None):
        calls=[]
        def register(root,candidate,*,lead_session_id,workflow=None):
            calls.append(lead_session_id)
            return {'candidate_id':candidate['id'],'snapshot_key':candidate['snapshot_key'],'session_id':lead_session_id,'state':'observing'}
        patches=[patch('tooling.agent_workflow.gate_liveness.register',side_effect=register),
                 patch.object(workflow_observer,'ensure_started',side_effect=(RuntimeError(observer_error) if observer_error else lambda *a,**k: object()))]
        for item in patches: item.start(); self.addCleanup(item.stop)
        return calls

    def test_prompt_size_failure_keeps_pre_admission_intake_and_block_progress(self):
        calls=self.intake_stubs()
        with patch.object(d,'_candidate_prompt',side_effect=d.DispatchError('bounded candidate instructions exceed prompt limit')), \
             patch.object(d,'_server',side_effect=AssertionError('must not contact service')):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        record=WF.readjson(self.path)['gate_request']
        self.assertEqual(result['status'],'blocked'); self.assertIn('prompt limit',result['detail'])
        self.assertEqual(calls,[None]); self.assertIsNone(record['session_id'])
        self.assertFalse(record['prompt_admitted']); self.assertIn('requested_at',record)

    def test_service_discovery_failure_leaves_durable_operational_error(self):
        calls=self.intake_stubs()
        with patch.object(d,'_server',side_effect=d.DispatchError('service unavailable')):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        request=WF.readjson(self.path)['gate_request']
        self.assertEqual(result['status'],'blocked'); self.assertIn('service unavailable',request['operational_error'])
        self.assertFalse(request['prompt_admitted']); self.assertIsNone(request['session_id'])
        self.assertEqual(calls,[None])

    def test_uncertain_session_creation_blocks_without_duplicate_post_on_retry(self):
        calls=self.intake_stubs(); posts=[]
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.startswith('/api/mcp?'):
                return 200,{'location':{'directory':str(self.central)},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]}
            if path.startswith('/api/agent/'): return 200,{'location':{'directory':str(self.central)},'data':{'name':'integration-lead'}}
            if path=='/api/session' and method=='POST':
                posts.append(body); raise d.DispatchError('transport timeout')
            raise AssertionError((method,path))
        with patch.object(d,'_server',return_value='http://opencode.test'), \
             patch.object(d,'_request',side_effect=api):
            first=d.request_gate(self.args(),self.root,workflow=WF)
            second=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(first['status'],'blocked'); self.assertEqual(second['status'],'blocked')
        self.assertEqual(len(posts),1); self.assertIn('unknown',second['detail'])
        self.assertTrue(WF.readjson(self.path)['gate_request']['creation_unknown'])
        self.assertEqual(calls,[None,None])

    def test_observer_start_failure_is_truthful_and_keeps_durable_intake(self):
        calls=self.intake_stubs()
        with patch.object(workflow_observer,'ensure_started',return_value=None),patch.object(d,'_server',side_effect=AssertionError('no service before observer')):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'blocked'); self.assertIn('observer startup failed',result['detail'])
        self.assertIn('unavailable',result['detail']); self.assertEqual(calls,[None])
        self.assertFalse(WF.readjson(self.path)['gate_request']['prompt_admitted'])
        self.assertEqual(WF.readjson(self.path)['gate_request']['operational_state'],'blocked')

    def test_successful_retry_clears_prior_operational_block_after_admission(self):
        calls=self.intake_stubs()
        with patch.object(d,'_server',side_effect=d.DispatchError('temporary service outage')):
            first=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(first['status'],'blocked')
        _,api=self.gate_api(outcome='ready_for_owner')
        with patch.object(d,'_server',return_value='http://opencode.test'), \
             patch.object(d,'_request',side_effect=api):
            second=d.request_gate(self.args(),self.root,workflow=WF)
        record=WF.readjson(self.path)['gate_request']
        self.assertEqual(second['status'],'admitted')
        self.assertNotIn('operational_error',record); self.assertNotIn('operational_state',record)
        self.assertEqual(calls,[None,None,'gate-sid'])

    def test_known_session_retry_reuses_session_and_saved_prompt_body(self):
        saved={'id':'msg_'+('c'*40),'text':'immutable saved text','delivery':'queue','resume':True,
               'metadata':{'workflow_gate_request':True,'candidate_id':'candidate-1','snapshot_key':'snap'}}
        WF.writejson(self.path,dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap',
            'central_worktree':str(self.central),'origin_session_id':'author-session','prompt_admitted':False,
            'prompt_id':saved['id'],'prompt_body':saved}))
        calls=self.intake_stubs(); state,api=self.gate_api(); sent=[]
        def capture(*args,**kwargs):
            if args[2].endswith('/prompt'):
                sent.append(args[3]); return 200,{'data':{'id':saved['id'],'sessionID':'gate-sid','type':'user','delivery':'queue',
                    'payload':{'text':saved['text'],'metadata':saved['metadata']}}}
            return api(*args,**kwargs)
        with patch.object(d,'_request',side_effect=capture),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertEqual(sent,[saved]); self.assertEqual(state['created'],0); self.assertEqual(calls,['gate-sid'])
        self.assertEqual(WF.readjson(self.path)['gate_request']['prompt_body'],saved)

    def test_deadline_distinguishes_active_proof_from_unknown_execution(self):
        for active,expected,phrase in ((True,'pending','still active'),(False,'blocked','operationally unknown')):
            with self.subTest(active=active):
                WF.writejson(self.path,self.c)
                self.intake_stubs(); state,api=self.gate_api()
                def observe(*args,**kwargs):
                    if active:
                        WF.writejson(self.root/'progress/candidate-1.json',{'candidate_id':'candidate-1','snapshot_key':'snap','phase':'running'})
                    return False
                with patch.object(d,'_request',side_effect=api),patch.object(d,'_wait_for_candidate_change',side_effect=observe):
                    result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
                self.assertEqual(result['status'],'admitted'); self.assertIn('API-acknowledged',result['detail'])

    def test_fresh_api_unavailable_returns_unknown_without_duplicate_dispatch(self):
        state,api=self.gate_api()
        request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
                 'origin_session_id':'author-session','prompt_admitted':True,'prompt_id':'already-admitted'}
        WF.writejson(self.path,dict(self.c,gate_request=request))
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid',
                'state':'api_unavailable','checked_at':__import__('time').time(),
                'reason':'active session lookup timed out; execution state unknown'}
        with patch.object(self.fake_liveness,'read_health',return_value=health), \
             patch.object(d,'_request',side_effect=api),patch.object(d,'_wait_for_candidate_change',side_effect=AssertionError('fresh API failure returns promptly')):
            result=d.request_gate(self.args(wait_timeout=7200),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertEqual((state['created'],state['prompts']),(0,0))

    def test_active_matching_health_still_waits_for_real_outcome_or_deadline(self):
        state,api=self.gate_api()
        request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
                 'origin_session_id':'author-session','prompt_admitted':True,'prompt_id':'already-admitted'}
        WF.writejson(self.path,dict(self.c,gate_request=request))
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid','state':'active'}
        with patch.object(self.fake_liveness,'read_health',return_value=health), \
             patch.object(d,'_request',side_effect=api),patch.object(d,'_wait_for_candidate_change',return_value=False) as wait:
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertEqual((state['created'],state['prompts']),(0,0)); self.assertFalse(wait.called)

    def test_hold_returns_held_before_candidate_or_api_work(self):
        (self.root / 'gate-maintenance.json').write_text(json.dumps({'owner': 'parent', 'note': 'drain', 'allowed_candidate_ids': []}))
        self.path.unlink()
        with patch.object(d, '_request', side_effect=AssertionError('must not call API')):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'held')
        self.assertFalse(self.path.exists())

    def test_dispatch_liveness_fixture_covers_package_attribute_and_module_cache(self):
        from tooling.agent_workflow import gate_liveness
        self.assertIs(gate_liveness,self.fake_liveness)
        self.assertIs(sys.modules['gate_liveness'],self.fake_liveness)
        self.assertIs(sys.modules['tooling.agent_workflow.gate_liveness'],self.fake_liveness)

    def test_new_gate_without_origin_is_rejected_before_any_api(self):
        args=self.args(); args.origin_session=None
        with patch.dict('os.environ',{},clear=True),patch.object(d,'_request',side_effect=AssertionError('no API before origin')):
            result=d.request_gate(args,self.root,workflow=WF)
        self.assertEqual(result['status'],'blocked'); self.assertIn('recorded author origin',result['detail'])

    def gate_api(self, *, outcome=None, session_agent='integration-lead', directory=None, timeout_on_wait=False,
                 mcp_status='connected', mcp_directory=None, mcp_rows=None):
        state = {'created': 0, 'prompts': 0, 'waits': 0}
        directory = directory or str(self.central)
        def call(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/mcp?'):
                return 200, {'location': {'directory': mcp_directory or str(self.central)},
                    'data': mcp_rows if mcp_rows is not None else [{'name': 'ms14_workflow', 'status': {'status': mcp_status}}]}
            if path.startswith('/api/agent/'):
                return 200, {'location': {'directory': str(self.central)}, 'data': {'name': 'integration-lead'}}
            if path == '/api/session' and method == 'POST':
                state['created'] += 1
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path == '/api/session/gate-sid' and method == 'GET':
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path.endswith('/prompt'):
                state['prompts'] += 1
                self.assertEqual(body['delivery'], 'queue')
                if body['text'] != 'immutable saved text':
                    self.assertIn('Independently assess implementation, parity and evidence', body['text'])
                    self.assertIn('unfinished work stays with author', body['text'])
                if body['text'] != 'immutable saved text':
                    self.assertIn('candidate_record', body['text'])
                    self.assertNotIn('workflow.py', body['text'])
                    self.assertNotIn('--asset-workspace', body['text'])
                self.assertRegex(body['id'],r'^msg_[0-9a-f]{40}$')
                for tool in ('ms14_workflow_review','ms14_workflow_finish'):
                    self.assertIn(tool,body['text'])
                for cli in ('gate-scope-review','gate-report --finish','gate-preflight','workflow.py'):
                    self.assertNotIn(cli,body['text'])
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

    def test_workflow_mcp_guard_keeps_intake_pending_until_connected_in_central_worktree(self):
        responses = [
            ('disconnected', {'status': 'disconnected'}),
            ('missing', []),
            ('unknown', [{'name': 'other', 'status': {'status': 'connected'}}]),
            ('wrong worktree', None),
        ]
        for label, rows in responses:
            with self.subTest(response=label):
                WF.writejson(self.path, self.c)
                self.intake_stubs()
                state, api = self.gate_api(mcp_status='connected' if rows is None else 'disconnected',
                    mcp_directory=str(self.author) if rows is None else None, mcp_rows=rows)
                with patch.object(d, '_request', side_effect=api):
                    result = d.request_gate(self.args(), self.root, workflow=WF)
                record = WF.readjson(self.path)['gate_request']
                self.assertIn(result['status'], ('pending', 'blocked'))
                self.assertTrue(record['dispatch_pending'])
                self.assertFalse(record.get('prompt_admitted', False))
                self.assertIsNone(record.get('session_id'))
                self.assertEqual((state['created'], state['prompts']), (0, 0))

        # Recovery admits the already-saved intake and uses the same prompt identity/body.
        state, api = self.gate_api()
        saved = {'id': 'msg_' + 'c' * 40, 'text': 'immutable saved text', 'delivery': 'queue', 'resume': True,
                 'metadata': {'workflow_gate_request': True, 'candidate_id': 'candidate-1', 'snapshot_key': 'snap'}}
        record = WF.readjson(self.path)['gate_request']
        record.update(origin_session_id='author-session', prompt_id=saved['id'], prompt_body=saved)
        WF.writejson(self.path, dict(self.c, gate_request=record))
        sent = []
        def capture(*args, **kwargs):
            if args[1] == 'POST' and args[2].endswith('/prompt'):
                sent.append(args[3])
                state['prompts'] += 1
                return 200, {'data': {'id': saved['id'], 'sessionID': 'gate-sid', 'type': 'user',
                    'delivery': 'queue', 'payload': {'text': saved['text'], 'metadata': saved['metadata']}}}
            return api(*args, **kwargs)
        with patch.object(d, '_request', side_effect=capture), patch.object(d, '_wait_for_candidate_change', return_value=False):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'admitted')
        self.assertEqual(sent, [saved])
        self.assertEqual((state['created'], state['prompts']), (1, 1))
        admitted = WF.readjson(self.path)['gate_request']
        self.assertEqual(admitted['prompt_id'], saved['id'])
        self.assertEqual(admitted['prompt_body'], saved)
        self.assertEqual(admitted['central_worktree'], str(self.central))

    def test_named_mcp_roster_contract_is_location_scoped_and_fail_closed(self):
        envelope=lambda rows, directory=None: {'location':{'directory':str(directory or self.central)},'data':rows}
        for state in ('connected','pending','disabled','failed','needs_auth'):
            reply=envelope([{'name':'ms14_workflow','status':{'status':state}}])
            self.assertEqual(state,d.named_mcp_state(reply,self.central,'ms14_workflow'))
        self.assertIsNone(d.named_mcp_state(envelope([]),self.central,'ms14_workflow'))
        for reply in (
            envelope([],self.author), {},
            {'location':{},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]},
            {'location':{'directory':''},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]},
            {'location':{'directory':None},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]},
            {'location':{'directory':str(self.central)},'data':{}},
            envelope(['malformed row']),
            envelope([{'name':'ms14_workflow','status':{}}]),
            envelope([{'name':'ms14_workflow','status':{'status':'unknown'}}]),
            envelope([{'name':'ms14_workflow','status':{'status':'connected'}},
                      {'name':'ms14_workflow','status':{'status':'connected'}}]),
        ):
            with self.subTest(reply=reply), self.assertRaises(ValueError):
                d.named_mcp_state(reply,self.central,'ms14_workflow')

    def test_missing_mcp_location_cannot_match_current_working_directory(self):
        reply={'location':{},'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]}
        with self.assertRaisesRegex(ValueError,'wrong worktree'):
            d.named_mcp_state(reply,Path.cwd(),'ms14_workflow')

    def test_ready_requires_fresh_core_publication_and_reuses_receipt(self):
        state, api = self.gate_api(outcome='ready_for_owner')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['status'], 'admitted', first)
        self.assertEqual(second['status'], 'ready_for_owner')
        self.assertEqual((state['created'], state['prompts'], state['waits']), (1, 1, 0))
        self.assertIn('verify_gate', WF.calls); self.assertIn('verify_publication', WF.calls)

    def test_rejection_is_report_bound_and_idempotent(self):
        state, api = self.gate_api(outcome='needs_changes')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            result = d.request_gate(self.args(), self.root, workflow=WF)
            again = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'admitted', result); self.assertEqual(again['detail'], ['fix this'])
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_wait_timeout_preserves_session_without_duplicate_prompt(self):
        state, api = self.gate_api(timeout_on_wait=True)
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['session_id'], 'gate-sid'); self.assertEqual(first['status'], 'admitted')
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
        self.assertEqual(result['status'],'admitted'); self.assertEqual((state['created'],state['prompts']),(0,0))
        self.assertEqual(json.loads(progress.read_text())['runner_pid'],456)
        self.assertEqual(json.loads(hp.read_text())['recovery_count'],4)

    def test_wait_health_block_cannot_override_durable_running_job(self):
        state,api=self.gate_api(timeout_on_wait=True)
        c=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
            'origin_session_id':'author-session','prompt_admitted':True,'prompt_id':'already-admitted'})
        WF.writejson(self.path,c)
        hp=self.root/'gate-health'/'candidate-1.json'; hp.parent.mkdir(parents=True,exist_ok=True)
        WF.writejson(hp,{'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid','state':'blocked','reason':'stale health'})
        jobdir=self.root/'gate-execution'; jobdir.mkdir()
        WF.writejson(jobdir/'candidate-1.json',{'job_id':'candidate-1','candidate_id':'candidate-1','snapshot_key':'snap',
            'lead_session_id':'gate-sid','state':'running'})
        with patch.object(d,'_request',side_effect=api),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertEqual((state['created'],state['prompts']),(0,0))

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
        self.assertEqual(second['status'],'admitted')
        self.assertEqual(prompts[0],prompts[1]); self.assertEqual(state['created'],1)

    def test_large_plan_is_kept_in_record_and_prompt_remains_bounded_pointer(self):
        self.c['gate_plan']['acceptance_criteria']=['criterion '+('x'*2000) for _ in range(10)]
        self.c['gate_plan']['checks']=['check '+('y'*2000) for _ in range(10)]
        self.c['steps']='step '*10000
        self.c['evidence']=[{'path':'evidence-'+str(i), 'sha256':'a'*64} for i in range(100)]
        prompt=d._candidate_prompt(self.c,self.root)
        self.assertLessEqual(len(prompt.encode('utf-8')),d.MAX_PROMPT)
        self.assertLessEqual(len(prompt.encode('utf-8')),2000)
        self.assertIn('candidate_record',prompt); self.assertIn('Read the complete plan',prompt)
        self.assertLess(len(prompt),1500)
        self.assertNotIn('criterion ',prompt); self.assertNotIn('evidence-0',prompt)

    def diagnostic_receipt(self, *, failed=True):
        path=self.author/'build'/'agent-workflow'/'fixture-sprint'/'reports'/'candidate-1-gate-validation.json'
        path.parent.mkdir(parents=True,exist_ok=True)
        check={'name':'diagnostic query','exit_code':1 if failed else 0,
               'evidence':{'status':'FAIL' if failed else 'PASS'}}
        path.write_text(json.dumps({'checks':[check],'purpose':'diagnostic'}),encoding='utf-8')
        digest=WF.sha(path)
        self.c.update(gate_plan={'kind':'diagnostic','checks':[{'argv':['python','-c','pass'],'rationale':'diagnostic','covers':[]}],
                                  'questions':['Why did startup fail?'],'manual_steps':[]},
                      gate_validation={'path':str(path),'sha256':digest},
                      gate_request={'origin_session_id':'author-session'},
                      gate_outcome={'result':'diagnostic_complete','purpose':'diagnostic','validation':str(path),
                                    'validation_sha256':digest})
        WF.writejson(self.path,self.c)
        return path

    def test_diagnostic_prompt_is_distinct_from_readiness_prompt(self):
        self.c['gate_plan']={'kind':'diagnostic','questions':['Why did startup fail?'],'checks':[]}
        prompt=d._candidate_prompt(self.c,self.root)
        self.assertIn('DIAGNOSTIC ONLY',prompt); self.assertIn('not readiness approval',prompt)
        self.assertIn('ms14_workflow_validate',prompt)
        self.assertNotIn('ms14_workflow_finish',prompt)
        self.assertNotIn('workflow.py',prompt)
        self.c['gate_plan']={'kind':'readiness','checks':[]}
        readiness=d._candidate_prompt(self.c,self.root)
        self.assertIn('Independently assess implementation',readiness)
        self.assertNotIn('DIAGNOSTIC ONLY',readiness)

    def test_diagnostic_terminal_receipt_returns_even_with_failed_check_and_rejects_forgery(self):
        path=self.diagnostic_receipt(failed=True)
        result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'diagnostic_complete'); self.assertEqual(result['detail'],str(path))
        self.c['gate_plan']={'kind':'readiness','checks':[]}; WF.writejson(self.path,self.c)
        forged=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(forged['status'],'blocked'); self.assertIn('not valid',forged['detail'])

    def test_diagnostic_completion_binds_receipt_hash_and_reuses_exact_acknowledged_body(self):
        path=self.diagnostic_receipt(failed=True); ref=WF.readjson(self.path)['gate_validation']; posted=[]
        def api(server,method,url,body=None,timeout=30):
            if url=='/api/info': return 200,{'version':'2.0.18'}
            if url.endswith('/author-session'):
                return 200,{'data':{'id':'author-session','agent':'author','directory':str(self.author)}}
            if url.endswith('/prompt'):
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError(url)
        with patch.object(d,'_request',side_effect=api):
            first=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
            second=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        expected='msg_'+hashlib.sha256(f"candidate-1\0snap\0diagnostic_complete\0{ref['sha256']}\0author-session".encode()).hexdigest()[:40]
        self.assertEqual(first['status'],'delivered'); self.assertEqual(second['status'],'delivered')
        self.assertEqual(len(posted),1); self.assertEqual(posted[0]['id'],expected)
        self.assertEqual(posted[0]['metadata']['report_hash'],ref['sha256'])
        self.assertIn('NOT readiness approval',posted[0]['text']); self.assertIn('FAIL',posted[0]['text'])
        self.assertIn(str(path),posted[0]['text'])
        path.write_text('{"checks":[],"purpose":"diagnostic"}',encoding='utf-8')
        with self.assertRaisesRegex(d.DispatchError,'stale'):
            d.deliver_gate_completion(self.args(),self.root,workflow=WF)

    def test_diagnostic_completion_rejects_stale_receipt_and_non_diagnostic_forgery(self):
        path=self.diagnostic_receipt(); path.write_text('{"checks":[]}',encoding='utf-8')
        with patch.object(d,'_request',side_effect=AssertionError('stale receipt must not send')):
            with self.assertRaisesRegex(d.DispatchError,'stale'):
                d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        self.c['gate_plan']={'kind':'readiness','checks':[]}; WF.writejson(self.path,self.c)
        with self.assertRaisesRegex(d.DispatchError,'not valid'):
            d.deliver_gate_completion(self.args(),self.root,workflow=WF)

    def test_large_findings_are_labeled_preview_and_preserved_in_bound_record(self):
        findings=['failure at path/file.py: assertion failed '+('detail '*400) for _ in range(20)]
        self.c.update(gate_request={'origin_session_id':'author-session'},
                      review={'result':'needs_changes','report_sha256':'rh'},
                      gate_outcome={'result':'needs_changes','report_sha256':'rh','findings':findings})
        WF.writejson(self.path,self.c)
        text=d._completion_text(self.c,'needs_changes')
        self.assertLessEqual(len(text.encode('utf-8')),2000)
        self.assertIn('PREVIEW ONLY',text)
        self.assertIn('read the FULL bound report before fixing or resubmitting',text)
        self.assertIn('path/file.py: assertion failed',text)
        self.assertEqual(WF.readjson(self.path)['gate_outcome']['findings'],findings)

    def test_single_huge_unicode_finding_is_preview_before_full_report_warning(self):
        finding='Failure at src/é.py: assert expected '+('unicode détail → '*400)
        self.c['gate_outcome']={'findings':[finding]}
        text=d._completion_text(self.c,'needs_changes')
        self.assertLessEqual(len(text.encode('utf-8')),2000)
        self.assertIn('PREVIEW ONLY',text)
        self.assertIn('read the FULL bound report before fixing or resubmitting',text)
        self.assertIn('src/é.py: assert expected',text)
        self.assertIn('[finding truncated; see full report]',text)
        self.assertEqual(self.c['gate_outcome']['findings'],[finding])

    def test_irreducible_unicode_pointer_over_limit_fails_before_api_or_session_creation(self):
        self.c['id']='candidate-' + ('é'*7000)
        self.path=self.root/'candidates'/(self.c['id']+'.json')
        # Exercise prompt sizing directly: UTF-8 rather than character count is the limit.
        with self.assertRaisesRegex(d.DispatchError,'prompt limit'):
            d._candidate_prompt(self.c,self.root)

    def test_saved_prompt_body_is_reused_byte_for_byte(self):
        state, api = self.gate_api()
        saved={'id':'msg_'+('a'*40),'text':'immutable saved text','delivery':'queue','resume':True,
               'metadata':{'workflow_gate_request':True,'candidate_id':'candidate-1','snapshot_key':'snap'}}
        current=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
            'origin_session_id':'author-session','prompt_admitted':False,'prompt_id':saved['id'],'prompt_body':saved})
        WF.writejson(self.path,current)
        sent=[]
        def api_capture(*args,**kwargs):
            if args[1]=='POST' and args[2].endswith('/prompt'):
                body=args[3]; sent.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'gate-sid','type':'user','delivery':'queue',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            return api(*args,**kwargs)
        with patch.object(d,'_request',side_effect=api_capture),patch.object(d,'_wait_for_candidate_change',return_value=False):
            d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(sent,[saved])
        self.assertEqual(state['created'],0)

    def test_oversized_saved_unadmitted_unicode_prompt_fails_pre_api_without_rewrite(self):
        body={'id':'msg_'+('b'*40),'text':'é'*(d.MAX_PROMPT//2+1),'delivery':'queue','resume':True,
              'metadata':{'workflow_gate_request':True,'candidate_id':'candidate-1','snapshot_key':'snap'}}
        self.assertGreater(len(body['text'].encode('utf-8')),d.MAX_PROMPT)
        request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
                 'origin_session_id':'author-session','prompt_admitted':False,'prompt_id':body['id'],'prompt_body':body}
        WF.writejson(self.path,dict(self.c,gate_request=request))
        before=WF.readjson(self.path)['gate_request']['prompt_body']
        with patch.object(d,'_server',side_effect=AssertionError('service must not be contacted')):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'blocked'); self.assertIn('prompt limit',result['detail'])
        saved=WF.readjson(self.path)['gate_request']
        self.assertEqual(saved['prompt_body'],before)
        self.assertIn('prompt limit',saved['operational_error'])

    def test_precreated_unadmitted_session_without_prompt_reuses_sid_and_persists_deterministic_prompt(self):
        _, api = self.gate_api()
        calls=[]
        def precreated(*args,**kwargs):
            calls.append((args[1],args[2],args[3] if len(args)>3 else kwargs.get('body')))
            return api(*args,**kwargs)
        request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
                 'origin_session_id':'author-session','prompt_admitted':False}
        WF.writejson(self.path,dict(self.c,gate_request=request))
        with patch.object(d,'_request',side_effect=precreated),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertFalse(any(method=='POST' and path=='/api/session' for method,path,_ in calls))
        prompts=[body for method,path,body in calls if method=='POST' and path.endswith('/prompt')]
        self.assertEqual(len(prompts),1)
        saved=WF.readjson(self.path)['gate_request']
        expected='msg_'+hashlib.sha256('candidate-1\0snap\0gate-sid'.encode()).hexdigest()[:40]
        self.assertEqual(prompts[0]['id'],expected)
        self.assertEqual(saved['prompt_id'],expected); self.assertEqual(saved['prompt_body'],prompts[0])
        self.assertTrue(saved['prompt_admitted'])

    def test_matching_blocked_health_returns_without_verdict(self):
        state, api = self.gate_api()
        original=__import__('tooling.agent_workflow.gate_liveness',fromlist=['register']).register
        def register(*args,**kwargs):
            h=original(*args,**kwargs); h.update(state='blocked',reason='lead exited')
            (self.root/'gate-health/candidate-1.json').write_text(json.dumps(h)); return h
        with patch.object(d,'_request',side_effect=api),patch('tooling.agent_workflow.gate_liveness.register',side_effect=register):
            result=d.request_gate(self.args(wait_timeout=10),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')

    def test_wrong_health_binding_is_ignored_and_expiry_preserves_runner(self):
        state, api = self.gate_api()
        original=__import__('tooling.agent_workflow.gate_liveness',fromlist=['register']).register
        def register(*args,**kwargs):
            h=original(*args,**kwargs); h.update(state='blocked',session_id='other-session')
            (self.root/'gate-health/candidate-1.json').write_text(json.dumps(h)); return h
        with patch.object(d,'_request',side_effect=api),patch('tooling.agent_workflow.gate_liveness.register',side_effect=register),patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        progress=json.loads((self.root/'progress/candidate-1.json').read_text())
        self.assertNotEqual(progress['phase'],'pending')

    def test_observer_bootstrap_is_requested_for_dispatch(self):
        state, api = self.gate_api(outcome='ready_for_owner')
        flags={'released':False}
        class Lock:
            def close(self): flags['released']=True
        with patch.object(WF,'gate_lock',side_effect=lambda path: Lock()),patch.object(d,'_request',side_effect=api):
            result=d.request_gate(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertTrue(self.observer_calls)

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
            return object()
        with patch.object(WF,'gate_lock',side_effect=lambda path: Lock()),patch.object(d,'_request',side_effect=api), \
             patch('tooling.agent_workflow.gate_liveness.register',side_effect=reg),patch.object(workflow_observer,'ensure_started',side_effect=observer), \
             patch.object(d,'_wait_for_candidate_change',return_value=False):
            result=d.request_gate(self.args(wait_timeout=1),self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted')
        self.assertEqual(events[0],('registered',False))
        self.assertEqual(events[1][0],'observer')
        self.assertEqual([event for event in events if isinstance(event,tuple) and event[0]=='registered'],
                         [('registered',False),('registered',False)])
        self.assertEqual(json.loads((self.root/'progress/candidate-1.json').read_text())['phase'],'admitted')

    def test_wrong_session_and_missing_persisted_outcome_block(self):
        state, api = self.gate_api(session_agent='review')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_wait_for_candidate_change', return_value=False):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked'); self.assertIn('unexpected role', result['detail'])
        state, api = self.gate_api()
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked', result); self.assertIn('unknown', result['detail'])

    def test_non_git_central_directory_is_accepted(self):
        # These fixture directories intentionally have no .git; dispatch must not
        # impose a shared git-common-dir requirement on the coordinator.
        self.assertFalse((self.central / '.git').exists())
        state, api = self.gate_api(outcome='ready_for_owner')
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'admitted', result)
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
        self.assertIn('2. Second finding',posted[0]['text'])
        self.assertIn('ms14_workflow_status',posted[0]['text'])
        self.assertNotIn('workflow.py',posted[0]['text'])
        WF.fail_verify=False

    def test_ready_completion_requires_author_manual_checks_from_supplied_plan(self):
        self.c.update(gate_request={'origin_session_id':'author-session'},
            gate_plan={'title':'Demo feature','manual_steps':['Open the demo room', 'Press the green switch'],
                       'fixture':'demo-room'},
            unverified='Multiplayer behavior was not exercised.',
            review={'result':'ready_for_owner','report_sha256':'rh'},
            gate_outcome={'result':'ready_for_owner','report_sha256':'rh'})
        WF.writejson(self.path,self.c)
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.endswith('/author-session'):
                return 200,{'data':{'id':'author-session','agent':'author','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path))
        with patch.object(d,'_request',side_effect=api):
            result=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
            again=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        self.assertEqual(result['status'],'delivered'); self.assertEqual(again['status'],'delivered')
        self.assertEqual(len(posted),1)
        text=posted[0]['text']
        self.assertIn('Steps: Open the demo room; Press the green switch.',text)
        self.assertIn('Fixture: demo-room.',text)
        self.assertIn('Recorded unverified boundary: Multiplayer behavior was not exercised.',text)
        self.assertIn('reply to owner with 3–5 short numbered checks',text)
        self.assertIn('include setup/action/expected result',text)
        self.assertIn('do not claim unavailable runtime testing',text)
        self.assertIn('If an exact instruction is missing, name that boundary',text)
        self.assertIn('No post-pass source edits just to repeat checks',text)
        self.assertIn('Technical pass is not owner acceptance.',text)

    def test_ready_completion_names_missing_manual_boundary_without_inventing_steps(self):
        self.c.update(gate_plan={'manual_steps':[]},unverified='',
                      gate_outcome={'result':'ready_for_owner'})
        text=d._completion_text(self.c,'ready_for_owner')
        self.assertIn('Steps: .',text)
        self.assertIn('If an exact instruction is missing, name that boundary',text)
        self.assertNotIn('Press',text)

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

    def test_failed_legacy_completion_reuses_exact_saved_body_after_formatter_changes(self):
        self.c.update(gate_request={'origin_session_id':'author-session'},
            review={'result':'needs_changes','report_sha256':'rh'},
            gate_outcome={'result':'needs_changes','report_sha256':'rh','findings':['fix']})
        msg_id='msg_'+hashlib.sha256('candidate-1\0snap\0needs_changes\0rh\0author-session'.encode()).hexdigest()[:40]
        old_body={'id':msg_id,'text':'Legacy saved notice text; immutable across retries.','delivery':'steer','resume':True,
            'metadata':{'workflow_gate_completion':True,'candidate_id':'candidate-1','snapshot_key':'snap',
                        'result':'needs_changes','report_hash':'rh'}}
        self.c['author_completion']={'id':msg_id,'origin_session_id':'author-session','candidate_id':'candidate-1',
            'snapshot_key':'snap','result':'needs_changes','report_hash':'rh','status':'failed','body':old_body}
        WF.writejson(self.path,self.c)
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.endswith('/author-session'): return 200,{'data':{'agent':'author','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                if len(posted)==1: raise d.DispatchError('ambiguous timeout')
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path))
        with patch.object(d,'_completion_text',side_effect=AssertionError('saved body must win')) as formatter, \
             patch.object(d,'_request',side_effect=api):
            first=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
            second=d.deliver_gate_completion(self.args(),self.root,workflow=WF)
        self.assertEqual((first['status'],second['status']),('failed','delivered'))
        self.assertEqual(posted,[old_body,old_body]); formatter.assert_not_called()

    def test_invalid_saved_completion_body_is_rejected_before_remote_api(self):
        self.c.update(gate_request={'origin_session_id':'author-session'},
            review={'result':'needs_changes','report_sha256':'rh'},
            gate_outcome={'result':'needs_changes','report_sha256':'rh','findings':['fix']})
        msg_id='msg_'+hashlib.sha256('candidate-1\0snap\0needs_changes\0rh\0author-session'.encode()).hexdigest()[:40]
        self.c['author_completion']={'id':msg_id,'origin_session_id':'author-session','candidate_id':'candidate-1',
            'snapshot_key':'snap','result':'needs_changes','report_hash':'rh','status':'failed',
            'body':{'id':msg_id,'text':'wrong binding','delivery':'steer','metadata':{'candidate_id':'other'}}}
        WF.writejson(self.path,self.c)
        with patch.object(d,'_request',side_effect=AssertionError('must reject before API')):
            with self.assertRaisesRegex(d.DispatchInputError,'stored author completion body'):
                d.deliver_gate_completion(self.args(),self.root,workflow=WF)

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
        with patch.object(d,'_server',return_value='http://opencode.test'), \
             patch.object(d,'_request',side_effect=api):
            result=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)
            again=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)
        self.assertEqual(result['status'],'delivered'); self.assertEqual(again['id'],result['id'])
        self.assertEqual(len(posted),1); self.assertTrue(posted[0]['metadata']['workflow_gate_blocked'])
        self.assertNotIn('author_notice',WF.readjson(self.path))
        self.assertEqual(json.loads((self.root/'gate-health/candidate-1.json').read_text())['author_notice']['status'],'delivered')
        WF.fail_verify=False

    def test_stale_source_block_notice_has_actionable_revision_boundary(self):
        self.c.update(gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap'},
                      worktree={'worktree':str(self.author)},agent='author')
        WF.writejson(self.path,self.c)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid',
                'state':'blocked','reason':'candidate source changed after submission'}
        posted=[]
        def api(server,method,url,body=None,timeout=30):
            if url=='/api/info': return 200,{'version':'2.0.18'}
            if url.endswith('/author-session'): return 200,{'data':{'id':'author-session','agent':'author','directory':str(self.author)}}
            if url.endswith('/prompt'):
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError(url)
        with patch.object(d,'_server',return_value='http://opencode.test'), \
             patch.object(d,'_request',side_effect=api):
            result=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF)
        self.assertEqual(result['status'],'delivered'); self.assertEqual(len(posted),1)
        text=posted[0]['text']
        self.assertIn('candidate source changed after submission',text)
        self.assertIn('Retry the same candidate only if its source and purpose are still fresh',text)
        self.assertIn('changed source requires a fresh revision',text)
        self.assertIn('No lead work is pending until dispatch is admitted',text)
        self.assertNotIn('owner',text.lower()); self.assertNotIn('corrected gate workflow',text)

    def test_receipt_bound_diagnostic_completion_suppresses_racing_block_notice(self):
        self.diagnostic_receipt(failed=True)
        current=WF.readjson(self.path)
        current['gate_request'].update(session_id='gate-sid',snapshot_key='snap')
        WF.writejson(self.path,current)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid',
                'state':'blocked','reason':'runner monitor observed session stopped'}
        with patch.object(d,'_request',side_effect=AssertionError('diagnostic terminal must suppress notice')):
            result=d.deliver_gate_block_notice(self.root,current,health,workflow=WF)
        self.assertEqual(result['status'],'finished')
        self.assertIn('diagnostic completion',result['reason'])
        self.assertFalse((self.root/'gate-health'/'candidate-1.json').exists())

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

    def test_interruption_notice_id_is_job_bound_and_retry_body_is_identical(self):
        self.c.update(gate_request={'origin_session_id':'author-session','session_id':'gate-sid','snapshot_key':'snap'},
                      worktree={'worktree':str(self.author)},agent='author')
        WF.writejson(self.path,self.c)
        health={'candidate_id':'candidate-1','snapshot_key':'snap','session_id':'gate-sid','state':'blocked',
                'reason':'worker exited; cleanup verified clear','execution_job_id':'job-7'}
        posted=[]
        def api(server,method,path,body=None,timeout=30):
            if path.endswith('/author-session'): return 200,{'data':{'agent':'author','directory':str(self.author)}}
            if path.endswith('/prompt'):
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'author-session','type':'user','delivery':'steer',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError(path)
        with patch.object(d,'_check_v2'),patch.object(d,'_server',return_value='server'):
            first=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF,request=api)
            second=d.deliver_gate_block_notice(self.root,self.c,health,workflow=WF,request=api)
        self.assertEqual(first['status'],'delivered'); self.assertEqual(second['status'],'delivered')
        self.assertEqual(len(posted),1); self.assertEqual(posted[0]['metadata']['execution_job_id'],'job-7')
        self.assertIn('cleanup verified clear',posted[0]['text']); self.assertEqual(posted[0]['id'],first['id'])

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
        self.c['gate_request']={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
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
                self.assertIn('acceptance_criteria', body['text']); return 200, {'data': {'id':body['id'],'sessionID':'review-sid','type':'user','delivery':'queue','payload':{'text':body['text'],'metadata':body['metadata']}}}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'fidelity-reviewer', 'finish': 'stop',
                    'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError((method, path))
        with patch.object(d, '_request', side_effect=api):
            args=SimpleNamespace(id='candidate-1', reviewer='fidelity-reviewer',actor='integration-lead',server='http://opencode.test')
            admission=d.scoped_review(args,self.root,workflow=WF)
            self.assertEqual(admission['status'],'admitted')
            result=d.scoped_review(args,self.root,workflow=WF)
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
        self.c['gate_request']={'session_id':'gate-sid','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
        WF.writejson(self.path,self.c)
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
            if path.endswith('/prompt'): return 200, {'data': {'id':body['id'],'sessionID':'sid','type':'user','delivery':'queue','payload':{'text':body['text'],'metadata':body['metadata']}}}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'review', 'finish': None, 'content': [{'type': 'text', 'text': json.dumps(report)}]},
                                        {'agent': 'review', 'finish': 'stop', 'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError(path)
        with patch.object(d, '_request', side_effect=api):
            args=SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',server='http://opencode.test')
            admission=d.scoped_review(args,self.root,workflow=WF)
            self.assertEqual(admission['status'],'admitted')
            result=d.scoped_review(args,self.root,workflow=WF)
        self.assertEqual(WF.readjson(result['report'])['decision'], 'needs_changes')

    def test_scoped_review_ambiguous_session_creation_is_not_retried_as_new_child(self):
        self.c['gate_request']={'session_id':'lead','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
        WF.writejson(self.path,self.c); calls=[]
        def api(server,method,path,body=None,timeout=30):
            calls.append((method,path,body))
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.startswith('/api/agent/'): return 200,{'location':{'directory':str(self.author)},'data':{'name':'review'}}
            if path=='/api/session' and method=='POST': raise d.DispatchError('ambiguous create timeout')
            raise AssertionError((method,path))
        args=SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',server='http://opencode.test')
        with patch.object(d,'_server',return_value='server'),patch.object(d,'_check_v2'),patch.object(d,'_request',side_effect=api):
            with self.assertRaisesRegex(d.DispatchError,'ambiguous create timeout'):
                d._dispatch_scoped_review(args,self.root,workflow=WF)
            with self.assertRaisesRegex(d.DispatchError,'creation outcome is unknown'):
                d._dispatch_scoped_review(args,self.root,workflow=WF)
        creates=[x for x in calls if x[1]=='/api/session']
        self.assertEqual(len(creates),1)
        self.assertTrue(WF.readjson(self.path)['scoped_reviews']['review']['admission_unknown'])

    def test_scoped_review_ambiguous_prompt_reuses_exact_body_and_known_child(self):
        self.c['gate_request']={'session_id':'lead','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
        self.c['scoped_reviews']={'review':{'session_id':'review-sid','snapshot_key':'snap','prompt_admitted':False,
            'admission_unknown':False,'dispatch_pending':True}}
        WF.writejson(self.path,self.c); prompts=[]; creates=[]; first={'fail':True}
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.startswith('/api/agent/'): return 200,{'location':{'directory':str(self.author)},'data':{'name':'review'}}
            if path=='/api/session/review-sid': return 200,{'data':{'id':'review-sid','agent':'review','directory':str(self.author)}}
            if path=='/api/session' and method=='POST': creates.append(body); raise AssertionError('must reuse known child')
            if path.endswith('/prompt'):
                prompts.append(body)
                if first['fail']:
                    first['fail']=False; raise d.DispatchError('ambiguous prompt timeout')
                return 200,{'data':{'id':body['id'],'sessionID':'review-sid','type':'user','delivery':'queue',
                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path))
        args=SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead',server='http://opencode.test')
        with patch.object(d,'_server',return_value='server'),patch.object(d,'_check_v2'),patch.object(d,'_request',side_effect=api):
            with self.assertRaisesRegex(d.DispatchError,'ambiguous prompt timeout'):
                d._dispatch_scoped_review(args,self.root,workflow=WF)
            result=d._dispatch_scoped_review(args,self.root,workflow=WF)
        self.assertEqual(result['status'],'admitted'); self.assertEqual(len(prompts),2); self.assertEqual(prompts[0],prompts[1])
        self.assertEqual(prompts[0]['id'][:4],'msg_'); self.assertFalse(creates)
        self.assertEqual(WF.readjson(self.path)['scoped_reviews']['review']['session_id'],'review-sid')

    def test_scoped_final_report_uses_existing_validator_as_deterministic_input_failure(self):
        self.c['gate_request']={'session_id':'lead','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
        self.c['scoped_reviews']={'review':{'session_id':'review-sid','snapshot_key':'snap','prompt_admitted':True,
            'collection_pending':True,'admission_unknown':False}}
        WF.writejson(self.path,self.c)
        report={'candidate_id':'candidate-1','snapshot_key':'snap','reviewer':'review','decision':'needs_changes','findings':['x']}
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.startswith('/api/agent/'): return 200,{'location':{'directory':str(self.author)},'data':{'name':'review'}}
            if path=='/api/session/review-sid': return 200,{'data':{'id':'review-sid','agent':'review','directory':str(self.author)}}
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200,{'data':[{'agent':'review','finish':'stop','content':[{'type':'text','text':json.dumps(report)}]}]}
            raise AssertionError((method,path))
        with patch.object(d,'_server',return_value='server'),patch.object(d,'_check_v2'),patch.object(d,'_request',side_effect=api), \
             patch.object(WF,'validate_review_report',side_effect=ValueError('report identity mismatch')):
            with self.assertRaisesRegex(d.DispatchInputError,'report identity mismatch'):
                d._dispatch_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead'),self.root,workflow=WF)

    def test_gate_deterministic_binding_origin_role_and_prompt_failures_clear_pending(self):
        cases=('central-binding','legacy-origin','known-role','oversized-prompt')
        for case in cases:
            with self.subTest(case=case):
                candidate=dict(self.c,gate_request={'session_id':'gate-sid','snapshot_key':'snap','central_worktree':str(self.central),
                    'origin_session_id':'author-session','prompt_admitted':False,'dispatch_pending':True})
                if case=='central-binding': candidate['gate_request']['central_worktree']=str(self.author)
                elif case=='legacy-origin': candidate['gate_request'].pop('origin_session_id')
                elif case=='oversized-prompt': candidate['gate_request']['prompt_body']={'text':'x'*(d.MAX_PROMPT+1)}
                WF.writejson(self.path,candidate)
                api_patch=patch.object(d,'_request',side_effect=self.gate_api(session_agent='review')[1] if case=='known-role' else AssertionError('terminal input should precede API'))
                with patch.object(d,'_server',return_value='server'),patch.object(d,'_check_v2'),api_patch:
                    result=d._dispatch_gate(self.args(),self.root,workflow=WF)
                saved=WF.readjson(self.path)['gate_request']
                self.assertEqual(result['status'],'blocked')
                self.assertFalse(saved['dispatch_pending']); self.assertEqual(saved['dispatch_state'],'blocked')

    def test_scoped_known_child_role_mismatch_is_terminal_input(self):
        self.c['gate_request']={'session_id':'lead','snapshot_key':'snap','prompt_admitted':True,'central_worktree':str(self.central)}
        self.c['scoped_reviews']={'review':{'session_id':'review-sid','snapshot_key':'snap','prompt_admitted':False,
            'dispatch_pending':True,'admission_unknown':False}}
        WF.writejson(self.path,self.c)
        def api(server,method,path,body=None,timeout=30):
            if path=='/api/info': return 200,{'version':'2.0.18'}
            if path.startswith('/api/agent/'): return 200,{'location':{'directory':str(self.author)},'data':{'name':'review'}}
            if path=='/api/session/review-sid': return 200,{'data':{'id':'review-sid','agent':'other','directory':str(self.author)}}
            raise AssertionError((method,path))
        with patch.object(d,'_server',return_value='server'),patch.object(d,'_check_v2'),patch.object(d,'_request',side_effect=api):
            with self.assertRaisesRegex(d.DispatchInputError,'known review session role/location mismatch'):
                d._dispatch_scoped_review(SimpleNamespace(id='candidate-1',reviewer='review',actor='integration-lead'),self.root,workflow=WF)

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
            with self.assertRaisesRegex(d.DispatchReplyTooLarge, 'exceeds limit') as raised:
                d._request(None, 'GET', '/api/info')
            self.assertIsInstance(raised.exception, d.DispatchError)
            self.assertEqual(raised.exception.code, 'REPLY_TOO_LARGE')
            self.assertEqual((raised.exception.actual_bytes, raised.exception.limit_bytes),
                             (d.MAX_REPLY + 1, d.MAX_REPLY))

    def test_explicit_reply_cap_is_bounded_and_invalid_caps_do_not_start_cli(self):
        raw = json.dumps({'data': [{'payload': 'x' * (d.MAX_REPLY + 100)}]})
        actual = len(raw.encode('utf-8'))
        response = SimpleNamespace(returncode=0, stdout=raw, stderr='')
        with patch.object(d.subprocess, 'run', return_value=response) as run:
            status, parsed = d._request(None, 'GET', '/api/session/sid/message?limit=10&order=desc',
                                        max_reply_bytes=d.HISTORY_REPLY_BYTES)
        self.assertEqual(status, 200)
        self.assertEqual(len(parsed['data'][0]['payload']), d.MAX_REPLY + 100)
        self.assertEqual(run.call_count, 1)
        with patch.object(d.subprocess, 'run', return_value=response):
            with self.assertRaises(d.DispatchReplyTooLarge) as raised:
                d._request(None, 'GET', '/api/info', max_reply_bytes=actual - 1)
        self.assertEqual((raised.exception.actual_bytes, raised.exception.limit_bytes), (actual, actual - 1))
        for cap in (0, -1, True, False, 1.5, '16m'):
            with self.subTest(cap=cap), patch.object(d.subprocess, 'run') as run:
                with self.assertRaises(d.DispatchError):
                    d._request(None, 'GET', '/api/info', max_reply_bytes=cap)
                run.assert_not_called()

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
