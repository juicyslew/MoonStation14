"""Offline owner-composition tests for the one-shot WR12 transition."""
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest import mock

import workflow_cutover_transition as transition


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class TransitionAssemblyTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.sequence=0
        self.local=self.root/'local';self.approved=self.local/'Temp'/'opencode';self.approved.mkdir(parents=True)
        env=mock.patch.dict(os.environ,{'LOCALAPPDATA':str(self.local)})
        env.start();self.addCleanup(env.stop)

    def make_plan(self, *, six_authors=False, dynamic_author=False, dynamic_author_count=1):
        self.sequence+=1;suffix=f'{self.sequence:02d}'
        main=self.root/'MoonStation14';exchange=self.root/'exchange';shared=self.root/'shared'
        for path in (main,exchange,shared): path.mkdir(exist_ok=True)
        worktrees=[main]
        for n in range(1,6):
            item=self.root/f'MoonStation14-agent-0{n}';item.mkdir(exist_ok=True);worktrees.append(item)
        source=shared/transition.workflow_cutover_process.OBSERVER_RELATIVE
        source.parent.mkdir(parents=True,exist_ok=True);source.write_text('# receipt-owned observer fixture\n',encoding='utf-8')
        source_hash=sha(source)
        old_receipt=self.approved/f'old-publication-{suffix}.json'
        old_receipt.write_text(json.dumps({'main_root':str(main),'shared_root':str(shared),'files':[{
            'target':'shared','path':transition.workflow_cutover_process.OBSERVER_RELATIVE.as_posix(),
            'deleted':False,'new_sha256':source_hash}]}),encoding='utf-8')
        pointer=main/transition.workflow_coordinator_package.POINTER_RELATIVE
        pointer.parent.mkdir(parents=True,exist_ok=True)
        pointer.write_text(json.dumps({'receipt':str(old_receipt),'bind_worktrees':False}),encoding='utf-8')
        exchange_hold=exchange/'gate-maintenance.json';exchange_hold.write_text('durable-held-intake',encoding='utf-8')
        hold_receipt=self.approved/f'hold-receipt-{suffix}.json'
        hold_receipt.write_text(json.dumps({'path':str(exchange_hold),'token':'hold-secret','sha256':'a'*64,
            'owner':'fixture-owner','control_id':'fixture-control'}),encoding='utf-8')
        legacy_configs={}
        receipt_data=json.loads(old_receipt.read_text(encoding='utf-8'))
        for index,worktree in enumerate(worktrees):
            config_path=worktree/'opencode.jsonc'
            legacy_entry={'type':'local','command':[str(shared/'.venv-workflow-mcp/Scripts/python.exe'),
                str(shared/'tooling/agent_workflow/workflow_mcp.py'),'--repo',str(worktree),'--profile','author'],
                'cwd':str(worktree),'environment':{},'disabled':True,'codemode':False,'protocol':'legacy',
                'timeout':{'startup':15000,'catalog':15000,'execution':300000}}
            config_path.write_text(json.dumps({'model':'preserve','mcp':{'servers':{
                'ms14_workflow':legacy_entry,'unrelated-server':{'type':'local','command':['do-not-touch']}}}}),encoding='utf-8')
            legacy_configs[worktree]=config_path
            receipt_data['files'].append({'target':'main' if index==0 else worktree.name,'path':'opencode.jsonc',
                'deleted':False,'new_sha256':sha(config_path)})
        old_receipt.write_text(json.dumps(receipt_data),encoding='utf-8')
        main_config=legacy_configs[main]
        token=self.approved/f'author-token-{suffix}';token.write_text('health-secret-value',encoding='utf-8')
        context_path=(self.approved/f'context-{suffix}.json') if dynamic_author else (self.root/f'context-{suffix}.json')
        if dynamic_author:
            floors={'required_packs':['package','provenance'],'selectors':{},'review_roles':[],
                'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100}
            author_rows=[]
            for index in range(dynamic_author_count):
                author_context_path=self.approved/f'context-{suffix}-{index}.json'
                context={'repo':str(main),'floors':floors,'publication_receipt_path':str(old_receipt),
                    'publication_receipt_sha256':sha(old_receipt)}
                author_context_path.write_text(json.dumps(context),encoding='utf-8')
                author_token=token if index==0 else self.approved/f'author-token-{suffix}-{index}'
                if index: author_token.write_text(f'health-secret-value-{index}',encoding='utf-8')
                author_rows.append({'caller':f'worktree-slot-{index}','token_file':str(author_token),
                    'context_file':str(author_context_path)})
            context_path=self.approved/f'context-{suffix}-0.json'
            author_bindings={'bindings':[],'author_worktrees':author_rows}
        else:
            context={'repo':str(main),'authenticated_author':{'session_id':'fixture-author','worktree':str(main),'role':'author'},
                'policy':{},'artifacts':[],'publication_receipt_path':str(old_receipt),
                'publication_receipt_sha256':sha(old_receipt)}
            author_bindings={'bindings':[{'caller':'author','token_file':str(token),'context_file':str(context_path)}]}
        context_path.write_text(json.dumps(context),encoding='utf-8')
        coordinator_config=self.root/f'coordinator-{suffix}.json'
        coordinator_config.write_text(json.dumps({'database':str(self.root/'db.sqlite'),
            'artifact_dir':str(self.root/'artifacts'),'opencode_server':'http://127.0.0.1:49374',
            'admission_root':str(exchange),
            **author_bindings}),encoding='utf-8')
        stage=self.root/f'package-stage-{suffix}';stage.mkdir()
        coordinator_key='tooling/agent_workflow/workflow_coordinator.py'
        staged_coordinator=stage/coordinator_key
        staged_coordinator.parent.mkdir(parents=True)
        coordinator_source=Path(transition.workflow_coordinator.__file__).resolve()
        staged_coordinator.write_bytes(coordinator_source.read_bytes())
        staged_sha=sha(staged_coordinator)
        (stage/'manifest.json').write_text(json.dumps({'schema_version':1,'package':'workflow-coordinator-inactive',
            'source_root':str(transition.workflow_coordinator_package.ROOT),
            'previous_receipt':{'path':str(old_receipt.resolve()),'sha256':sha(old_receipt)},'files':{
                coordinator_key:{'source':coordinator_key,'target':coordinator_key,'sha256':staged_sha,
                    'source_sha256':sha(coordinator_source),'receipt_target':'shared','receipt_path':coordinator_key}}}),encoding='utf-8')
        config_hash=sha(coordinator_config)
        bindings=[]
        bound_worktrees=worktrees
        for index,worktree in enumerate(bound_worktrees):
            bindings.append({'session_id':f'session-{index}','agent':'orchestrate-ms14','worktree':str(worktree)})
        identity={'pid':501,'created':987654}
        plan={
            'version':1,'plan_sha256':'','operation_id':f'transition-fixture-{suffix}',
            'operation_root':str(self.approved/f'wr12-transition-fixture-{suffix}'),
            'main_root':str(main),'exchange_root':str(exchange),'shared_root':str(shared),
            'hold_receipt_path':str(hold_receipt),'bindings':bindings,'lower_bound_ms':100,
            'api_server':'http://127.0.0.1:49374',
            'legacy_mcp':[{'name':'ms14_workflow','worktree':str(worktree),'config_path':str(config_path),'config_sha256':sha(config_path)}
                for worktree,config_path in legacy_configs.items()],
            'previous_receipt_path':str(old_receipt),'expected_receipt_sha256':sha(old_receipt),
            'expected_observer_identity':identity,
            'history_output_root':str(self.approved/f'wr12-legacy-history-fixture-{suffix}'),
            'stage':str(stage),'publication_receipt_path':str(self.approved/f'wr12-publication-new-{suffix}.json'),
            'pointer_path':str(pointer),'expected_pointer_sha256':sha(pointer),
            'config_path':str(coordinator_config),'health_file':str(self.root/f'supervisor-health-{suffix}.json'),
            'health_token_file':str(token),'port':8030,'activation_nonce':'fixture-nonce',
            'expected_config_sha256':config_hash,
            'expected_runtime_entry_sha256':sha(transition.workflow_supervisor.__file__),
            'expected_python_sha256':sha(__import__('sys')._base_executable),
        }
        plan['plan_sha256']=transition._plan_digest(plan)
        return plan,{'main':main,'exchange':exchange,'shared':shared,'source':source,
            'hold_file':exchange_hold,'hold_sha':sha(exchange_hold),'old_receipt':old_receipt,
            'pointer':pointer,'stage':stage,'main_config':main_config,'coordinator_config':coordinator_config,
            'token':token,'identity':identity,'worktrees':worktrees,'legacy_configs':legacy_configs}

    def test_plan_accepts_six_author_sessions_without_role_uniqueness(self):
        plan,_=self.make_plan(six_authors=True)
        validated=transition._validate_plan(plan)
        self.assertEqual(6,len(validated['plan']['bindings']))
        self.assertEqual({'orchestrate-ms14'},{item['agent'] for item in plan['bindings']})

    def test_plan_requires_supervisor_admission_root_to_match_exchange(self):
        for value in (None, '', 'admission-root', str(self.root / 'other-admission-root')):
            with self.subTest(value=value):
                plan, files = self.make_plan()
                config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
                if value is None: config.pop('admission_root')
                else: config['admission_root']=value
                files['coordinator_config'].write_text(json.dumps(config),encoding='utf-8')
                plan['expected_config_sha256']=sha(files['coordinator_config'])
                plan['plan_sha256']=transition._plan_digest(plan)
                with self.assertRaisesRegex(ValueError, 'admission root'):
                    transition._validate_plan(plan)

    def test_transition_plan_requires_each_of_six_retirement_rows_even_when_redigested(self):
        for omitted in range(6):
            with self.subTest(omitted=omitted):
                plan,_=self.make_plan()
                plan['legacy_mcp'].pop(omitted)
                plan['plan_sha256']=transition._plan_digest(plan)
                with self.assertRaisesRegex(ValueError,'exactly the six known worktree configs'):
                    transition.run_transition(plan)
                self.assertFalse(Path(plan['operation_root']).exists())
        plan,_=self.make_plan();plan['legacy_mcp'].append(dict(plan['legacy_mcp'][0]))
        plan['plan_sha256']=transition._plan_digest(plan)
        with self.assertRaisesRegex(ValueError,'duplicate legacy MCP worktree'):
            transition.run_transition(plan)
        self.assertFalse(Path(plan['operation_root']).exists())

    def test_enabled_receipt_owned_legacy_entry_holds_before_journal_or_effects(self):
        plan,files=self.make_plan()
        config=json.loads(files['main_config'].read_text(encoding='utf-8'))
        config['mcp']['servers']['ms14_workflow']['disabled']=False
        files['main_config'].write_text(json.dumps(config),encoding='utf-8')
        old=json.loads(files['old_receipt'].read_text(encoding='utf-8'))
        next(row for row in old['files'] if row['target']=='main' and row['path']=='opencode.jsonc')['new_sha256']=sha(files['main_config'])
        files['old_receipt'].write_text(json.dumps(old),encoding='utf-8')
        plan['expected_receipt_sha256']=sha(files['old_receipt'])
        plan['legacy_mcp'][0]['config_sha256']=sha(files['main_config'])
        staged_manifest=Path(plan['stage'])/'manifest.json'
        staged=json.loads(staged_manifest.read_text(encoding='utf-8'))
        staged['previous_receipt']['sha256']=plan['expected_receipt_sha256']
        staged_manifest.write_text(json.dumps(staged),encoding='utf-8')
        plan['plan_sha256']=transition._plan_digest(plan)
        with self.assertRaisesRegex(ValueError,'persistently disabled'):
            transition.run_transition(plan)
        self.assertFalse(Path(plan['operation_root']).exists())

    def test_config_only_retirement_package_is_not_an_activation_candidate(self):
        plan,_=self.make_plan()
        manifest_path=Path(plan['stage'])/'manifest.json'
        manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
        manifest['package']='workflow-legacy-retirement';manifest['files']={}
        manifest['bindings']={'status':'planned-legacy-retirement','mode':'legacy-retirement'}
        manifest_path.write_text(json.dumps(manifest),encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'canonical shared coordinator entry'):
            transition._validate_plan(plan)
        self.assertFalse(Path(plan['operation_root']).exists())

    def test_plan_replay_and_authority_hash_drift_refuse_before_journal(self):
        plan,_=self.make_plan();plan['plan_sha256']=transition._plan_digest(plan)
        Path(plan['operation_root']).mkdir()
        with self.assertRaisesRegex(ValueError,'output path already exists'): transition.run_transition(plan)
        self.assertFalse((Path(plan['operation_root'])/'transition.json').exists())
        plan2,_=self.make_plan()
        Path(plan2['config_path']).write_text('{}',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'config hash drift'): transition.run_transition(plan2)
        self.assertFalse(Path(plan2['operation_root']).exists())
        plan4,files4=self.make_plan()
        files4['main_config'].write_text('{}',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'legacy MCP config hash mismatch'): transition.run_transition(plan4)
        self.assertFalse(Path(plan4['operation_root']).exists())
        plan3,_=self.make_plan();plan3['api_server']='https://example.com';plan3['plan_sha256']=transition._plan_digest(plan3)
        with self.assertRaises(ValueError): transition.run_transition(plan3)
        self.assertFalse(Path(plan3['operation_root']).exists())

    def test_package_manifest_must_bind_exact_shared_coordinator_before_effects(self):
        plan,_=self.make_plan()
        manifest_path=Path(plan['stage'])/'manifest.json'
        manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
        manifest['files']['tooling/agent_workflow/workflow_coordinator.py']['receipt_target']='main'
        manifest_path.write_text(json.dumps(manifest),encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'canonical shared coordinator entry'):
            transition.run_transition(plan)
        self.assertFalse(Path(plan['operation_root']).exists())

    def _execute(self, *, api_statuses=None, post_result=(204,None), post_error=None,
                  roster_after='absent', roster_before='connected', inventory_status='OBSERVED_CLEAR', stop_status='STOPPED',
                  reappear_at=None, barrier_held_at=None, config_drift_before_delete=False,
                 publish_status='applied', listener=None, child_identity=None, process_identity_override=None, health_drift=False,
                 admin_identity_query_fail=False, self_receipt_override=None, barrier_exit_failures=0,
                 activation_journal_failure=False, published_coordinator_tampered=False,
                  dynamic_author=False, dynamic_author_count=1, context_mutation_before_publish=False,
                  refresh_write_error=False, health_token_kind=None, slow_mcp_proofs=False):
        plan,files=self.make_plan(dynamic_author=dynamic_author,dynamic_author_count=dynamic_author_count)
        config_at_preflight=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
        context_original_bytes=[Path(row['context_file']).read_bytes() for row in config_at_preflight.get('author_worktrees',[])]
        if health_token_kind:
            config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
            health_token=self.approved/f'{health_token_kind}-only-token'
            health_token.write_text(f'{health_token_kind}-only-secret',encoding='utf-8')
            if health_token_kind=='reviewer':
                config['reviewers']={'fidelity':{'worktree':str(files['main']),
                    'agent':'workflow-reviewer-fidelity','token_file':str(health_token),'caller':'reviewer'}}
            files['coordinator_config'].write_text(json.dumps(config),encoding='utf-8')
            plan['health_token_file']=str(health_token)
            plan['expected_config_sha256']=sha(files['coordinator_config'])
            plan['plan_sha256']=transition._plan_digest(plan)
        state={'disconnected':False,'disconnected_worktrees':set(),'deletes':0,'mcp_calls':[],'probe_calls':[],'stop_calls':[],
               'collect_calls':[],'history_calls':[],'publish_calls':[],'supervisors':[],
                 'barrier':None,'health_requests':[],'cleanup':False,'roster_gets':0,'contexts_at_supervisor':[],
                 'logical_ms':1000,'inventory_ms':None,'stop_age_ms':None}
        api_statuses=api_statuses or ['OBSERVED_CLEAR','OBSERVED_CLEAR']
        class Barrier:
            def __init__(self,root,hold):
                state['barrier']=self;self.root=Path(root);self.hold=hold;self.owner=__import__('threading').get_ident();self.exits=0;self.checks=0
            @property
            def _owner_thread(self): return self.owner
            def __enter__(self): return self
            def recheck(self):
                self.checks+=1
                status='HELD' if barrier_held_at==self.checks else 'OBSERVED_ZERO'
                return {'status':status,'jobs':[],'check':self.checks}
            def __exit__(self,*args):
                self.exits+=1
                if self.exits<=barrier_exit_failures: raise OSError(f'fixture barrier release failure {self.exits}')
                return False
        def probe(bindings,lower_bound_ms,request=None,server=None,max_pages=100):
            state['probe_calls'].append((bindings,lower_bound_ms,server))
            index=len(state['probe_calls'])-1
            return {'status':api_statuses[min(index,len(api_statuses)-1)],'reason_codes':[],'evidence':[]}
        def request(server,method,path,body=None,timeout=30):
            state['mcp_calls'].append((server,method,path,timeout))
            if slow_mcp_proofs and method=='GET': state['logical_ms']+=7000
            if method=='POST':
                state['deletes']+=1
                if post_error: raise post_error
                worktree=transition.urllib.parse.parse_qs(transition.urllib.parse.urlsplit(path).query)['location[directory]'][0]
                if roster_after in {'absent','disabled','reappear_before_stop','reappear_before_publication'}:
                    state['disconnected_worktrees'].add(worktree);state['disconnected']=True
                return post_result
            if method=='GET' and path.startswith('/api/mcp?'):
                state['roster_gets']+=1
                worktree=transition.urllib.parse.parse_qs(transition.urllib.parse.urlsplit(path).query)['location[directory]'][0]
                get_count=sum(1 for call in state['mcp_calls'] if call[1]=='GET' and
                    transition.urllib.parse.parse_qs(transition.urllib.parse.urlsplit(call[2]).query).get('location[directory]')==[worktree])
                reappeared=(worktree==str(files['main']) and reappear_at=='observer_stop' and get_count==4 or
                            worktree==str(files['main']) and reappear_at=='publication' and get_count==6)
                if reappeared:
                    rows=[{'name':'ms14_workflow','status':{'status':'connected'}}]
                elif worktree in state['disconnected_worktrees'] and roster_after=='pending_then_absent':
                    rows=[] if get_count >= 4 else [{'name':'ms14_workflow','status':{'status':'pending'}}]
                elif worktree in state['disconnected_worktrees']:
                    rows=[] if roster_after!='disabled' else [{'name':'ms14_workflow','status':{'status':'disabled'}}]
                else:
                    status=roster_before
                    rows=[] if status=='missing' else [{'name':'ms14_workflow','status':{'status':status}}]
                rows.append({'name':'unrelated-server','status':{'status':'failed'}})
                if config_drift_before_delete and worktree==str(files['main']) and get_count==1:
                    files['main_config'].write_text('{}',encoding='utf-8')
                return 200,{'location':{'directory':worktree},'data':rows}
            raise AssertionError((method,path))
        observer_plan={'exchange_root':str(files['exchange']),'shared_root':str(files['shared']),
            'publication_receipt':str(files['old_receipt']),'expected_publication_receipt_sha256':plan['expected_receipt_sha256'],
            'pid':plan['expected_observer_identity']['pid'],'created':plan['expected_observer_identity']['created'],
            'executable_path':'python.exe','command_line':'python observer --watch','source_sha256':sha(files['source']),
            'observed_at_ms':1000,'inventory':{'source_digest':'fresh'}}
        def collect(barrier,previous_receipt_path,expected_receipt_sha,expected_observer_identity,shared_root,
                    query_processes=None,clock_ms=None,argv_parser=None):
            state['collect_calls'].append((barrier,previous_receipt_path,expected_receipt_sha,expected_observer_identity,shared_root))
            state['inventory_ms']=state['logical_ms']
            observer_plan['observed_at_ms']=state['inventory_ms']
            if inventory_status!='OBSERVED_CLEAR': return {'status':inventory_status,'reasons':['fixture inventory hold']}
            state['fresh_observer_plan']=observer_plan
            return {'status':'OBSERVED_CLEAR','observer_plan':observer_plan,
                    'inventory':{'source_digest':'fixture'},'evidence':{'snapshot_sha256':'0'*64}}
        def stop(observed,*,barrier,query_cim=None,win_api=None,argv_parser=None,clock_ms=None,max_plan_age_ms=30000,timeout_ms=5000):
            state['stop_calls'].append((observed,barrier))
            state['stop_age_ms']=state['logical_ms']-observed['observed_at_ms']
            if state['stop_age_ms']>max_plan_age_ms: return {'status':'HELD','reason':'stale fixture plan'}
            return {'status':stop_status,'pid':observed['pid']}
        def capture(exchange,private_new_output_root,previous_receipt_path,expected_receipt_sha,operation_id):
            state['history_calls'].append((exchange,private_new_output_root,previous_receipt_path,expected_receipt_sha,operation_id))
            return {'manifest':str(Path(private_new_output_root)/'manifest.json'),'manifest_sha256':'d'*64}
        def publish(stage,previous_receipt,receipt_output,*,pointer_path,expected_pointer_sha256,rollback_receipt=None):
            state['publish_calls'].append((stage,previous_receipt,receipt_output,pointer_path,expected_pointer_sha256,rollback_receipt))
            if context_mutation_before_publish:
                dynamic_config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
                target=Path(dynamic_config['author_worktrees'][0]['context_file'])
                changed=json.loads(target.read_text(encoding='utf-8'));changed['floors']['review_envelope_limit']=999
                target.write_text(json.dumps(changed),encoding='utf-8')
            if publish_status=='applied':
                Path(receipt_output).write_text(json.dumps({'status':'applied','actions':[]}),encoding='utf-8')
                shared_entry=files['shared']/'tooling/agent_workflow/workflow_coordinator.py'
                shared_entry.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(Path(stage)/'tooling/agent_workflow/workflow_coordinator.py',shared_entry)
                if published_coordinator_tampered: shared_entry.write_text('tampered after publication',encoding='utf-8')
            return {'status':publish_status,'actions':[]}
        self_id={'pid':os.getpid(),'created':70001}
        actual_child=child_identity or {'pid':701,'created':70101}
        def write_health():
            supervisor={'identity_status':'verified','identity':self_id,'activation_nonce':plan['activation_nonce'],
                'config_path':plan['config_path'],'config_sha256':plan['expected_config_sha256'],
                'runtime_entry_path':str(Path(transition.workflow_supervisor.__file__).resolve()),
                'runtime_entry_sha256':plan['expected_runtime_entry_sha256'],
                'python_path':str(Path(__import__('sys')._base_executable).resolve()),'python_sha256':plan['expected_python_sha256']}
            if health_drift: supervisor['runtime_entry_sha256']='0'*64
            Path(plan['health_file']).write_text(json.dumps({'state':'running','supervisor':supervisor,
                'child':{'pid':actual_child['pid'],'creation_identity':actual_child,
                         'coordinator_identity':actual_child,'scope_name':'workflow-coordinator'}}),encoding='utf-8')
        def fake_supervisor_run(supervisor):
            dynamic_config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
            state['contexts_at_supervisor']=[json.loads(Path(row['context_file']).read_text(encoding='utf-8'))
                for row in dynamic_config.get('author_worktrees',[])]
            state['supervisors'].append((supervisor.database,supervisor.config,supervisor.health_file,
                plan['port'],supervisor.activation_nonce,supervisor.on_running,
                supervisor.command[0],supervisor.command[1]))
            write_health()
            receipt={'supervisor_identity':self_receipt_override or self_id,'coordinator_identity':actual_child,
                'scope_name':'workflow-coordinator','scope_member':True,'ready_go':True,
                'activation_nonce':plan['activation_nonce'],'config_path':plan['config_path'],
                'config_sha256':plan['expected_config_sha256'],
                'runtime_entry_path':str(Path(transition.workflow_supervisor.__file__).resolve()),
                'runtime_entry_sha256':plan['expected_runtime_entry_sha256'],
                'python_path':str(Path(__import__('sys')._base_executable).resolve()),
                'python_sha256':plan['expected_python_sha256']}
            try: supervisor.on_running(receipt)
            except Exception: state['cleanup']=True

        class FakeResponse:
            status=200
            def __enter__(self): return self
            def __exit__(self,*args): return False
            def read(self,size): return json.dumps({'status':'running','pending_events':0,'last_error':None}).encode()
        class FakeOpener:
            def open(self,request,timeout):
                state['health_requests'].append((request.full_url,request.get_header('Authorization'),timeout))
                return FakeResponse()
        listeners=listener if listener is not None else [{'LocalPort':plan['port'],'State':'Listen','OwningProcess':actual_child['pid']}]
        def identity(pid):
            if pid==os.getpid(): return None if admin_identity_query_fail else self_id
            return process_identity_override if process_identity_override is not None else actual_child

        def record(self,phase,status,receipt=None):
            if activation_journal_failure and phase=='activation_proof': raise OSError('fixture activation journal write failure')
            return original_record(self,phase,status,receipt)
        with ExitStack() as stack:
            stack.enter_context(mock.patch.object(transition.workflow_cutover,'DrainBarrier',Barrier))
            api_mock=stack.enter_context(mock.patch.object(transition.workflow_cutover_api,'probe_cutover',autospec=True,side_effect=probe))
            request_mock=stack.enter_context(mock.patch.object(transition.gate_dispatch,'_request',autospec=True,side_effect=request))
            inventory_mock=stack.enter_context(mock.patch.object(transition.workflow_cutover_inventory,'collect',autospec=True,side_effect=collect))
            stop_mock=stack.enter_context(mock.patch.object(transition.workflow_cutover_process,'stop_observer',autospec=True,side_effect=stop))
            history_mock=stack.enter_context(mock.patch.object(transition.workflow_cutover_history,'capture',autospec=True,side_effect=capture))
            publish_mock=stack.enter_context(mock.patch.object(transition.workflow_coordinator_package,'publish',autospec=True,side_effect=publish))
            supervisor_mock=stack.enter_context(mock.patch.object(transition.workflow_supervisor.WorkflowSupervisor,'run',autospec=True,side_effect=fake_supervisor_run))
            if isinstance(listener, list) and listener and isinstance(listener[0], list):
                listener_mock=stack.enter_context(mock.patch.object(transition,'_query_listeners',side_effect=iter(listener)))
            else:
                listener_mock=stack.enter_context(mock.patch.object(transition,'_query_listeners',return_value=listeners))
            process_mock=stack.enter_context(mock.patch.object(transition.gate_execution,'process_identity',side_effect=identity))
            original_record=transition._Journal.record
            if activation_journal_failure:
                stack.enter_context(mock.patch.object(transition._Journal,'record',autospec=True,side_effect=record))
            opener_mock=stack.enter_context(mock.patch.object(transition.urllib.request,'build_opener',return_value=FakeOpener()))
            hold_mock=stack.enter_context(mock.patch.object(transition.admission,'verified_hold_handle',autospec=True,side_effect=AssertionError('transition must not acquire hold separately')))
            if refresh_write_error:
                original_atomic=transition.workflow_coordinator_package._atomic_write
                config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
                failed_context=Path(config['author_worktrees'][0]['context_file'])
                def fail_context_write(path,data,*,exclusive=False):
                    if Path(path)==failed_context: raise OSError('fixture context write failure')
                    return original_atomic(path,data,exclusive=exclusive)
                stack.enter_context(mock.patch.object(transition.workflow_coordinator_package,'_atomic_write',side_effect=fail_context_write))
            try:
                result=transition.run_transition(plan)
                state['result']=result
            except Exception as exc:
                state['error']=exc
            state['mocks']={'api':api_mock,'request':request_mock,'inventory':inventory_mock,'stop':stop_mock,
                'history':history_mock,'publish':publish_mock,'supervisor':supervisor_mock,'listener':listener_mock,
                'process':process_mock,'opener':opener_mock,'hold':hold_mock}
        state['plan']=plan;state['files']=files
        state['context_original_bytes']=context_original_bytes
        journal_path=Path(plan['operation_root'])/'transition.json'
        state['journal']=json.loads(journal_path.read_text(encoding='utf-8')) if journal_path.exists() else None
        return state

    def test_slow_mcp_proofs_precede_fresh_age_bounded_observer_inventory(self):
        state=self._execute(slow_mcp_proofs=True)
        self.assertNotIn('error',state,state.get('error'))
        self.assertEqual('AWAITING_TOOL_PROOF',state['result']['status'])
        self.assertGreater(state['logical_ms'],31000)
        self.assertLessEqual(state['stop_age_ms'],30000)
        self.assertEqual(1,state['mocks']['inventory'].call_count)
        self.assertEqual(1,state['mocks']['stop'].call_count)
        phases=[row['phase'] for row in state['journal']['phases']]
        self.assertLess(phases.index('mcp_absence_observer_stop_effect'),phases.index('process_inventory'))
        self.assertLess(phases.index('process_inventory'),phases.index('observer_stop_intent'))

    def test_healthy_owner_composition_uses_actual_signatures_and_public_barrier_release(self):
        state=self._execute()
        self.assertNotIn('error',state,state.get('error'))
        self.assertEqual('AWAITING_TOOL_PROOF',state['result']['status'])
        self.assertEqual(1,state['barrier'].exits)
        self.assertGreaterEqual(state['barrier'].checks,15)
        self.assertEqual(state['files']['hold_sha'],sha(state['files']['hold_file']))
        self.assertEqual(0,state['mocks']['hold'].call_count)
        self.assertEqual(2,state['mocks']['api'].call_count)
        state['mocks']['api'].assert_any_call(state['plan']['bindings'],state['plan']['lower_bound_ms'],server=state['plan']['api_server'])
        state['mocks']['inventory'].assert_called_once_with(state['barrier'],state['files']['old_receipt'],
            state['plan']['expected_receipt_sha256'],state['plan']['expected_observer_identity'],state['files']['shared'])
        self.assertEqual(1,state['mocks']['stop'].call_count)
        stop_args=state['mocks']['stop'].call_args
        self.assertIs(state['fresh_observer_plan'],stop_args.args[0])
        self.assertEqual(state['plan']['expected_observer_identity']['pid'],stop_args.args[0]['pid'])
        self.assertEqual(1,state['mocks']['history'].call_count)
        self.assertEqual((state['files']['exchange'],Path(state['plan']['history_output_root']),state['files']['old_receipt'],
                          state['plan']['expected_receipt_sha256'],state['plan']['operation_id']),state['mocks']['history'].call_args.args)
        self.assertEqual(1,state['mocks']['publish'].call_count)
        pub=state['mocks']['publish'].call_args
        self.assertEqual((state['files']['stage'],state['files']['old_receipt'],Path(state['plan']['publication_receipt_path'])),pub.args)
        self.assertEqual({'pointer_path':state['files']['pointer'],'expected_pointer_sha256':state['plan']['expected_pointer_sha256']},pub.kwargs)
        supervisor=state['supervisors'][0]
        self.assertEqual(state['plan']['config_path'],str(supervisor[1]))
        self.assertEqual((self.root/'db.sqlite').resolve(),Path(supervisor[0]).resolve())
        self.assertEqual(str(Path(__import__('sys').executable).resolve()),str(Path(supervisor[6]).resolve()))
        self.assertEqual(str((state['files']['shared']/'tooling/agent_workflow/workflow_coordinator.py').resolve()),
                         str(Path(supervisor[7]).resolve()))
        self.assertEqual(str(Path(__import__('sys')._base_executable).resolve()),state['journal']['python_path'])
        self.assertEqual(sha(__import__('sys')._base_executable),state['journal']['python_sha256'])
        self.assertNotEqual(Path(__import__('sys').executable).resolve(),Path(__import__('sys')._base_executable).resolve())
        self.assertEqual('Bearer health-secret-value',state['health_requests'][0][1])
        self.assertEqual(6,state['deletes'])
        delete_calls=[c for c in state['mcp_calls'] if c[1]=='POST']
        self.assertEqual(6,len(delete_calls))
        self.assertIn('/api/experimental/mcp/ms14_workflow/disconnect?',delete_calls[0][2])
        self.assertIn('location%5Bdirectory%5D=',delete_calls[0][2])
        delete_index=next(i for i,c in enumerate(state['mcp_calls']) if c[1]=='POST')
        self.assertEqual('GET',state['mcp_calls'][delete_index+1][1])
        self.assertNotIn('health-secret-value',json.dumps(state['journal']))
        self.assertEqual(state['plan']['plan_sha256'],state['journal']['plan_sha256'])
        self.assertEqual(os.getpid(),state['mocks']['process'].call_args_list[0].args[0])
        self.assertEqual(os.getpid(),state['journal']['admin_identity']['pid'])

    def test_health_token_accepts_static_and_dynamic_author_bindings_only(self):
        dynamic=self._execute(dynamic_author=True,dynamic_author_count=2)
        self.assertNotIn('error',dynamic,dynamic.get('error'))
        dynamic_config=json.loads(dynamic['files']['coordinator_config'].read_text(encoding='utf-8'))
        self.assertEqual([],dynamic_config['bindings'])
        self.assertEqual(str(Path(dynamic['plan']['health_token_file']).resolve()),
                         str(Path(dynamic_config['author_worktrees'][0]['token_file']).resolve()))
        context_path=Path(dynamic_config['author_worktrees'][0]['context_file'])
        refreshed=json.loads(context_path.read_text(encoding='utf-8'))
        self.assertEqual(str(Path(dynamic['plan']['publication_receipt_path']).resolve()),refreshed['publication_receipt_path'])
        self.assertEqual(sha(dynamic['plan']['publication_receipt_path']),refreshed['publication_receipt_sha256'])
        self.assertEqual({'required_packs':['package','provenance'],'selectors':{},'review_roles':[],
            'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100},refreshed['floors'])
        initial_contexts=json.loads(dynamic['files']['coordinator_config'].read_text(encoding='utf-8'))['author_worktrees']
        self.assertEqual(2,len(dynamic['contexts_at_supervisor']))
        for row,observed in zip(initial_contexts,dynamic['contexts_at_supervisor']):
            prior=json.loads(Path(row['context_file']).read_text(encoding='utf-8'))
            self.assertEqual(str(Path(dynamic['plan']['publication_receipt_path']).resolve()),prior['publication_receipt_path'])
            self.assertEqual(sha(dynamic['plan']['publication_receipt_path']),prior['publication_receipt_sha256'])
            self.assertEqual(str(dynamic['files']['main'].resolve()),prior['repo'])
            self.assertEqual(refreshed['floors'],prior['floors'])
            self.assertEqual(observed,prior)
        phases=[row['phase'] for row in dynamic['journal']['phases']]
        self.assertLess(phases.index('publication_result'),phases.index('author_context_refresh'))
        self.assertLess(phases.index('author_context_refresh'),phases.index('supervisor_start_intent'))

        for kind in ('unregistered','reviewer'):
            with self.subTest(kind=kind):
                rejected=self._execute(health_token_kind=kind)
                self.assertIn('error',rejected)
                self.assertIsNone(rejected['barrier'])
                self.assertEqual([],rejected['mcp_calls'])
                self.assertEqual(0,rejected['mocks']['stop'].call_count)
                self.assertEqual(0,rejected['mocks']['publish'].call_count)
                self.assertEqual(0,rejected['mocks']['supervisor'].call_count)
                self.assertIsNone(rejected['journal'])

    def test_dynamic_context_cas_and_atomic_write_failure_never_start_supervisor(self):
        changed=self._execute(dynamic_author=True,dynamic_author_count=2,context_mutation_before_publish=True)
        self.assertIn('error',changed)
        self.assertEqual(0,changed['mocks']['supervisor'].call_count)
        changed_contexts=json.loads(changed['files']['coordinator_config'].read_text(encoding='utf-8'))['author_worktrees']
        # The mutation is retained, and the untouched peer was not rewritten before the all-context CAS failed.
        self.assertEqual(Path(changed_contexts[1]['context_file']).read_bytes(),changed['context_original_bytes'][1])

        write_failed=self._execute(dynamic_author=True,refresh_write_error=True)
        self.assertIn('error',write_failed)
        self.assertEqual(0,write_failed['mocks']['supervisor'].call_count)
        self.assertEqual(write_failed['files']['hold_sha'],sha(write_failed['files']['hold_file']))
        row=json.loads(write_failed['files']['coordinator_config'].read_text(encoding='utf-8'))['author_worktrees'][0]
        failed_context=json.loads(Path(row['context_file']).read_text(encoding='utf-8'))
        self.assertEqual(str(write_failed['files']['old_receipt'].resolve()),failed_context['publication_receipt_path'])
        self.assertEqual(write_failed['plan']['expected_receipt_sha256'],failed_context['publication_receipt_sha256'])

    def test_dynamic_context_must_bind_previous_receipt_before_effects(self):
        for mutation, expected in (("stale", "previous receipt"), ("missing", "missing or not a regular file"),
                                   ("outside_private", "private OpenCode temp")):
            with self.subTest(mutation=mutation):
                plan,files=self.make_plan(dynamic_author=True)
                config=json.loads(files['coordinator_config'].read_text(encoding='utf-8'))
                context_path=Path(config['author_worktrees'][0]['context_file'])
                if mutation=='stale':
                    context=json.loads(context_path.read_text(encoding='utf-8'))
                    context['publication_receipt_sha256']='0'*64
                    context_path.write_text(json.dumps(context),encoding='utf-8')
                elif mutation=='missing':
                    context_path.unlink()
                else:
                    config['author_worktrees'][0]['context_file']=str(self.root/'context-outside-private.json')
                    (self.root/'context-outside-private.json').write_text(context_path.read_text(encoding='utf-8'),encoding='utf-8')
                    files['coordinator_config'].write_text(json.dumps(config),encoding='utf-8')
                    plan['expected_config_sha256']=sha(files['coordinator_config'])
                    plan['plan_sha256']=transition._plan_digest(plan)
                with self.assertRaisesRegex(ValueError,expected):
                    transition._validate_plan(plan)
                self.assertFalse(Path(plan['operation_root']).exists())

    def test_api_active_removal_ambiguous_inventory_unknown_and_stop_error_hold(self):
        cases=[
            ({'api_statuses':['HELD']},0,0,0),
            ({'post_error':OSError('ambiguous'),'roster_after':'connected'},1,0,0),
            ({'inventory_status':'HELD'},6,0,0),
            ({'stop_status':'ERROR'},6,1,0),
        ]
        for overrides,deletes,stops,publishes in cases:
            with self.subTest(overrides=overrides):
                state=self._execute(**overrides)
                self.assertIn('error',state)
                self.assertEqual(1,state['barrier'].exits)
                self.assertEqual(deletes,len([c for c in state['mcp_calls'] if c[1]=='POST']))
                self.assertEqual(stops,state['mocks']['stop'].call_count)
                self.assertEqual(publishes,state['mocks']['publish'].call_count)
                self.assertEqual(state['files']['hold_sha'],sha(state['files']['hold_file']))

    def test_ambiguous_removal_reconciles_once_and_unlisted_server_is_not_removed(self):
        reconciled=self._execute(post_result=(500,{'error':'ambiguous'}),roster_after='absent')
        self.assertNotIn('error',reconciled)
        self.assertEqual(6,reconciled['deletes'])
        self.assertEqual(6,len([call for call in reconciled['mcp_calls'] if call[1]=='POST']))
        missing=self._execute(roster_before='missing')
        self.assertNotIn('error',missing)
        self.assertEqual(0,missing['deletes'])
        self.assertNotIn('POST',[call[1] for call in missing['mcp_calls']])

        disabled=self._execute(roster_before='disabled')
        self.assertNotIn('error',disabled)
        self.assertEqual(0,disabled['deletes'])

    def test_connected_runtime_disconnects_then_persisted_disabled_runtime_proves_inactive(self):
        state=self._execute(roster_before='connected',roster_after='disabled')
        self.assertNotIn('error',state,state.get('error'))
        disconnect=[call for call in state['mcp_calls'] if call[1]=='POST']
        self.assertEqual(6,len(disconnect))
        self.assertIn('/ms14_workflow/disconnect?',disconnect[0][2])
        self.assertFalse(any(call[1]=='DELETE' for call in state['mcp_calls']))
        self.assertIn('PROVED_INACTIVE',[(row['phase'],row['status']) for row in state['journal']['phases']]
                      and [row['status'] for row in state['journal']['phases'] if row['phase']=='mcp_removal_result'])

    def test_named_row_reappearance_blocks_observer_stop_and_publication_effects(self):
        before_stop=self._execute(reappear_at='observer_stop')
        self.assertIn('error',before_stop)
        self.assertEqual(0,before_stop['mocks']['stop'].call_count)
        self.assertEqual(0,before_stop['mocks']['publish'].call_count)
        before_publish=self._execute(reappear_at='publication')
        self.assertIn('error',before_publish)
        self.assertEqual(1,before_publish['mocks']['stop'].call_count)
        self.assertEqual(0,before_publish['mocks']['publish'].call_count)

    def test_removal_preconditions_and_held_barrier_block_delete(self):
        held=self._execute(barrier_held_at=2)
        self.assertIn('error',held)
        self.assertEqual(0,held['deletes'])
        drift=self._execute(config_drift_before_delete=True)
        self.assertIn('error',drift)
        self.assertEqual(0,drift['deletes'])

    def _direct_remove(self, states, *, delete_error=None, delete_advance=0, response_advances=None,
                       barrier_advances=None, journal_advance=None, roster_override=None, held_barrier=False,
                       monotonic_samples=None, sleep_advance=None):
        plan,files=self.make_plan()
        # This helper exercises one target's timed reconciliation in isolation.
        plan['legacy_mcp']=[plan['legacy_mcp'][0]]
        row=plan['legacy_mcp'][0]
        self.sequence+=1
        root=self.root/f'direct-journal-{self.sequence}'
        journal=transition._Journal(root,plan['plan_sha256'],{'pid':1,'created':1},'nonce',Path(__import__('sys').executable),'0'*64)
        clock={'now':100.0,'gets':0,'delete_calls':[],'timeouts':[]}
        monotonic_samples=list(monotonic_samples or [])
        response_advances=response_advances or {}
        barrier_advances=barrier_advances or {}
        def advance(value): clock['now']+=value
        def fake_sleep(seconds): advance(seconds if sleep_advance is None else sleep_advance)
        def monotonic():
            if monotonic_samples:
                clock['now']=monotonic_samples.pop(0)
            return clock['now']
        class Barrier:
            def __init__(self): self.checks=0
            def recheck(self):
                self.checks+=1
                advance(barrier_advances.get(self.checks,0))
                return {'status':'HELD' if held_barrier and self.checks==1 else 'OBSERVED_ZERO'}
        barrier=Barrier()
        def request(server,method,path,body=None,timeout=30):
            if method=='POST':
                clock['delete_calls'].append((path,timeout))
                clock['timeouts'].append(timeout)
                self.assertGreater(timeout,0)
                advance(delete_advance)
                if delete_error: raise delete_error
                return 204,None
            self.assertEqual('GET',method)
            clock['timeouts'].append(timeout)
            self.assertGreater(timeout,0)
            self.assertIn('location%5Bdirectory%5D=',path)
            index=clock['gets'];clock['gets']+=1
            if index>0: self.assertLessEqual(timeout,7)
            advance(response_advances.get(index,0))
            if roster_override is not None:
                return roster_override(index,files)
            state=states[min(index,len(states)-1)]
            rows=[] if state=='absent' else [{'name':'ms14_workflow','status':{'status':state}}]
            rows.append({'name':'unrelated','status':{'status':'failed'}})
            return 200,{'location':{'directory':str(files['main'])},'data':rows}
        original_record=transition._Journal.record
        def record(journal_obj,phase,status,receipt=None):
            result=original_record(journal_obj,phase,status,receipt)
            if journal_advance and phase==journal_advance[0] and status==journal_advance[1]: advance(journal_advance[2])
            return result
        with ExitStack() as stack:
            stack.enter_context(mock.patch.object(transition.time,'monotonic',side_effect=monotonic))
            stack.enter_context(mock.patch.object(transition.time,'sleep',side_effect=fake_sleep))
            stack.enter_context(mock.patch.object(transition.gate_dispatch,'_request',side_effect=request))
            stack.enter_context(mock.patch.object(transition._Journal,'record',autospec=True,side_effect=record))
            error=None
            try: transition._remove_legacy(plan,journal,barrier)
            except Exception as exc: error=exc
        return {'error':error,'journal':journal.data,'barrier':barrier,'clock':clock,'worktree':row['worktree']}

    def test_scoped_removal_pending_absence_and_ambiguous_delete_reconcile_once(self):
        passed=self._direct_remove(['connected','pending','absent','absent'])
        self.assertIsNone(passed['error'])
        calls=passed['clock']['delete_calls']
        self.assertEqual(1,len(calls))
        self.assertIn('/api/experimental/mcp/ms14_workflow/disconnect?',calls[0][0])
        query=transition.urllib.parse.parse_qs(transition.urllib.parse.urlsplit(calls[0][0]).query)
        self.assertEqual([passed['worktree']],query['location[directory]'])
        self.assertEqual('connected',next(row['value']['state'] for row in passed['journal']['receipts']
                                          if row['phase']=='mcp_removal_precondition'))
        self.assertEqual(["pending","absent","absent"],[
            row['value']['state'] for row in passed['journal']['receipts']
            if row['phase']=='mcp_removal_reconcile'])
        ambiguous=self._direct_remove(['connected','absent','absent'],delete_error=OSError('ambiguous'))
        self.assertIsNone(ambiguous['error'])
        self.assertEqual(1,len(ambiguous['clock']['delete_calls']))
        self.assertEqual('transport_error',next(row['value']['result_state'] for row in ambiguous['journal']['receipts']
                                                 if row['phase']=='mcp_removal_http'))

    def test_204_without_two_absences_and_pending_timeout_hold_with_last_state(self):
        disabled=self._direct_remove(['connected','disabled','disabled'],response_advances={1:0})
        self.assertIsNone(disabled['error'])
        self.assertEqual('PROVED_INACTIVE',next(row['status'] for row in disabled['journal']['phases']
                                                if row['phase']=='mcp_removal_result'))
        pending=self._direct_remove(['connected','pending'],journal_advance=('mcp_removal_reconcile','OBSERVED',12))
        self.assertIsInstance(pending['error'],transition.TransitionHeld)
        self.assertEqual('pending',next(row['value']['state'] for row in pending['journal']['receipts']
                                        if row['phase']=='mcp_removal_reconcile'))
        timed_out=self._direct_remove(['connected','pending'],sleep_advance=12)
        self.assertIsInstance(timed_out['error'],transition.TransitionHeld)
        self.assertEqual('pending',next(row['value']['state'] for row in reversed(timed_out['journal']['receipts'])
                                        if row['phase']=='mcp_removal_reconcile'))

    def test_late_absence_and_barrier_or_journal_deadline_overruns_are_not_accepted(self):
        late=self._direct_remove(['connected','absent'],response_advances={1:12})
        self.assertIsInstance(late['error'],transition.TransitionHeld)
        late_observation=next(row for row in late['journal']['receipts'] if row['phase']=='mcp_removal_reconcile')
        self.assertEqual('absent',late_observation['value']['state'])
        self.assertEqual('LATE_NOT_ACCEPTED',next(phase['status'] for phase in late['journal']['phases']
                                                   if phase['phase']=='mcp_removal_reconcile'))
        late_second=self._direct_remove(['connected','absent','absent'],response_advances={2:12})
        self.assertIsInstance(late_second['error'],transition.TransitionHeld)
        second_absence=next(row for row in reversed(late_second['journal']['receipts'])
                            if row['phase']=='mcp_removal_reconcile')
        self.assertEqual('absent',second_absence['value']['state'])
        late_phases=[row for row in late_second['journal']['phases'] if row['phase']=='mcp_removal_reconcile']
        self.assertEqual('LATE_NOT_ACCEPTED',late_phases[-1]['status'])
        barrier_before_get=self._direct_remove(['connected','absent'],barrier_advances={2:12})
        self.assertIsInstance(barrier_before_get['error'],transition.TransitionHeld)
        self.assertEqual(1,barrier_before_get['clock']['gets'])
        barrier_after_absence=self._direct_remove(['connected','absent','absent'],barrier_advances={4:12})
        self.assertIsInstance(barrier_after_absence['error'],transition.TransitionHeld)
        result_states=[row['status'] for row in barrier_after_absence['journal']['phases'] if row['phase']=='mcp_removal_result']
        self.assertNotIn('REMOVED',result_states)
        journal_overrun=self._direct_remove(['connected','absent','absent'],journal_advance=('mcp_removal_reconcile','OBSERVED',12))
        self.assertIsInstance(journal_overrun['error'],transition.TransitionHeld)
        result_states=[row['status'] for row in journal_overrun['journal']['phases'] if row['phase']=='mcp_removal_result']
        self.assertNotIn('REMOVED',result_states)

    def test_roster_rejects_missing_empty_none_unknown_duplicate_and_wrong_scope(self):
        worktree=str(Path.cwd())
        for directory in ('',None):
            with self.subTest(directory=directory), mock.patch.object(transition.gate_dispatch,'_request',return_value=(200,{
                    'location':{'directory':directory},'data':[]})):
                with self.assertRaises(transition.TransitionHeld): transition._mcp_roster('http://127.0.0.1',worktree)
        with mock.patch.object(transition.gate_dispatch,'_request',return_value=(200,{'location':{},'data':[]})):
            with self.assertRaises(transition.TransitionHeld): transition._mcp_roster('http://127.0.0.1',worktree)
        with mock.patch.object(transition.gate_dispatch,'_request',return_value=(200,{
                'location':{'directory':str(Path(worktree).parent)},'data':[]})):
            with self.assertRaises(transition.TransitionHeld): transition._mcp_roster('http://127.0.0.1',worktree)
        with mock.patch.object(transition.gate_dispatch,'_request',return_value=(200,{'location':{'directory':worktree},'data':[]})):
            self.assertEqual([],transition._mcp_roster('http://127.0.0.1',worktree))
        for rows in ([{'name':'ms14_workflow','status':{'status':'unknown'}}],
                     [{'name':'ms14_workflow','status':{'status':'connected'}},
                      {'name':'ms14_workflow','status':{'status':'connected'}}]):
            result=self._direct_remove(['connected'],roster_override=lambda index,files:(200,{
                'location':{'directory':str(files['main'])},'data':rows}))
            self.assertIsInstance(result['error'],transition.TransitionHeld)
        failed=self._direct_remove(['connected'],roster_override=lambda index,files:(503,{'error':'private'}))
        self.assertIsInstance(failed['error'],transition.TransitionHeld)
        self.assertEqual(503,next(row['value']['http_status'] for row in failed['journal']['receipts']
                                  if row['phase']=='mcp_removal_precondition'))

    def test_held_barrier_prevents_delete(self):
        result=self._direct_remove(['connected'],held_barrier=True)
        self.assertIsInstance(result['error'],transition.TransitionHeld)
        self.assertEqual([],result['clock']['delete_calls'])

    def test_expired_delete_budget_and_late_result_journal_cannot_succeed(self):
        expired=self._direct_remove(['connected'],monotonic_samples=[100,112])
        self.assertIsInstance(expired['error'],transition.TransitionHeld)
        self.assertEqual([],expired['clock']['delete_calls'])
        journal_late=self._direct_remove(['connected','absent','absent'],
                                          journal_advance=('mcp_removal_result','PROVED_INACTIVE',12))
        self.assertIsInstance(journal_late['error'],transition.TransitionHeld)
        result_statuses=[row['status'] for row in journal_late['journal']['phases']
                         if row['phase']=='mcp_removal_result']
        self.assertEqual(['PROVED_INACTIVE','HELD'],result_statuses)

    def test_publish_partial_is_persisted_and_never_starts_supervisor(self):
        state=self._execute(publish_status='partial',dynamic_author=True)
        self.assertIn('error',state)
        phases=[row['phase'] for row in state['journal']['phases']]
        self.assertIn('publication_result',phases)
        partial=next(row['value'] for row in state['journal']['receipts'] if row['phase']=='publication_result')
        self.assertEqual('partial',partial['status'])
        self.assertEqual(0,state['mocks']['supervisor'].call_count)
        self.assertEqual(1,state['barrier'].exits)
        row=json.loads(state['files']['coordinator_config'].read_text(encoding='utf-8'))['author_worktrees'][0]
        context=json.loads(Path(row['context_file']).read_text(encoding='utf-8'))
        self.assertEqual(str(state['files']['old_receipt'].resolve()),context['publication_receipt_path'])
        self.assertEqual(state['plan']['expected_receipt_sha256'],context['publication_receipt_sha256'])

    def test_tampered_published_shared_coordinator_is_retained_but_never_started(self):
        state=self._execute(published_coordinator_tampered=True)
        self.assertIn('error',state)
        self.assertNotEqual('AWAITING_TOOL_PROOF',state['journal']['status'])
        self.assertEqual(0,state['mocks']['supervisor'].call_count)
        self.assertTrue(Path(state['plan']['publication_receipt_path']).is_file())
        evidence=next(row['value'] for row in state['journal']['receipts']
                      if row['phase']=='published_coordinator_integrity')
        self.assertEqual(sha(Path(state['plan']['stage'])/'tooling/agent_workflow/workflow_coordinator.py'),
                         evidence['expected_sha256'])
        self.assertEqual('applied',next(row['value']['status'] for row in state['journal']['receipts']
                                        if row['phase']=='publication_result'))
        self.assertEqual(state['files']['hold_sha'],sha(state['files']['hold_file']))

    def test_wrong_listener_pid_reuse_and_health_hash_drift_refuse_activation(self):
        cases=[
            {'listener':[{'LocalPort':8030,'State':'Listen','OwningProcess':999}]},
            {'process_identity_override':{'pid':701,'created':777}},
            {'health_drift':True},
        ]
        for override in cases:
            with self.subTest(override=override):
                state=self._execute(**override)
                self.assertIn('error',state)
                self.assertEqual(1,state['barrier'].exits)
                self.assertTrue(state['cleanup'])
                self.assertNotEqual('AWAITING_TOOL_PROOF',state['journal']['status'])
                if override.get('health_drift'):
                    self.assertEqual(0,state['mocks']['listener'].call_count)
                self.assertEqual(state['files']['hold_sha'],sha(state['files']['hold_file']))

    def test_health_proof_retries_initial_no_listener_then_binds_exact_child(self):
        actual=[{'LocalPort':8030,'State':'Listen','OwningProcess':701}]
        state=self._execute(listener=[[],actual])
        self.assertNotIn('error',state,state.get('error'))
        self.assertEqual('AWAITING_TOOL_PROOF',state['result']['status'])
        self.assertEqual(2,state['mocks']['listener'].call_count)
        self.assertEqual(1,len(state['health_requests']))

    def test_current_admin_identity_is_journaled_before_effects_and_receipt_must_match(self):
        unavailable=self._execute(admin_identity_query_fail=True)
        self.assertIn('error',unavailable)
        self.assertIsNone(unavailable['barrier'])
        self.assertEqual([],unavailable['mcp_calls'])
        self.assertEqual(0,unavailable['mocks']['stop'].call_count)
        self.assertEqual(0,unavailable['mocks']['publish'].call_count)
        self.assertIsNone(unavailable['journal'])

        wrong=self._execute(self_receipt_override={'pid':os.getpid()+20,'created':70001})
        self.assertIn('error',wrong)
        self.assertNotEqual('AWAITING_TOOL_PROOF',wrong['journal']['status'])
        self.assertEqual(os.getpid(),wrong['journal']['admin_identity']['pid'])
        self.assertEqual(70001,wrong['journal']['admin_identity']['created'])
        self.assertEqual(wrong['plan']['activation_nonce'],wrong['journal']['activation_nonce'])
        self.assertEqual(wrong['plan']['expected_python_sha256'],wrong['journal']['python_sha256'])
        self.assertEqual(str(Path(__import__('sys')._base_executable).resolve()),wrong['journal']['python_path'])
        self.assertEqual(1,wrong['barrier'].exits)
        self.assertEqual(wrong['files']['hold_sha'],sha(wrong['files']['hold_file']))

    def test_barrier_release_failures_and_post_release_journal_failure_never_report_success(self):
        state=self._execute(barrier_exit_failures=2)
        self.assertIn('error',state)
        self.assertNotEqual('AWAITING_TOOL_PROOF',state['journal']['status'])
        self.assertEqual(2,state['barrier'].exits)
        phases=[row['phase'] for row in state['journal']['phases']]
        self.assertIn('barrier_release_failure',phases)
        self.assertIn('barrier_cleanup',phases)
        errors=[row['value'] for row in state['journal']['receipts'] if row['phase']=='barrier_release_failure']
        self.assertEqual('fixture barrier release failure 1',errors[0]['detail'])
        cleanup=next(row['value'] for row in state['journal']['receipts'] if row['phase']=='barrier_cleanup')
        self.assertFalse(cleanup['verified_release'])
        self.assertEqual(state['files']['hold_sha'],sha(state['files']['hold_file']))

        journal_failure=self._execute(activation_journal_failure=True)
        self.assertIn('error',journal_failure)
        self.assertEqual(1,journal_failure['barrier'].exits)
        self.assertNotEqual('AWAITING_TOOL_PROOF',journal_failure['journal']['status'])
        self.assertIn('transition_error',[row['phase'] for row in journal_failure['journal']['phases']])
        self.assertEqual(journal_failure['files']['hold_sha'],sha(journal_failure['files']['hold_file']))

    def _listener_serialization_fixture(self):
        main=Path(transition.__file__).resolve().parents[2]
        proof_path=main/'build/agent-workflow/workflow-redesign-plan/wr12/attempts/' \
            'listener-state-serialization-diagnostic-56b3f691-e1a4-4bed-a197-f7d716c54952/proof.json'
        self.assertEqual(sha(proof_path),'d3a84360b5bf3464374b37ba1543512b5f74d35229f7b876423af4158e617e9e')
        proof=json.loads(proof_path.read_text(encoding='utf-8'))
        observation=proof['observation']
        self.assertEqual(observation['state_enum_type'],
            'Microsoft.PowerShell.Cmdletization.GeneratedTypes.NetTCPConnection.State')
        self.assertEqual(observation['listen_enum_underlying_value'],2)
        return observation['same_select_convert_to_json'],observation['explicit_string_normalized_json']

    def test_default_listener_reader_normalizes_real_enum_serialization_and_keeps_strict_schema(self):
        numeric_json,normalized_json=self._listener_serialization_fixture()
        class Result:
            def __init__(self,stdout): self.stdout=stdout
        expected={'LocalPort':12345,'State':'Listen','OwningProcess':12345}
        with mock.patch.object(transition.subprocess,'run',return_value=Result(normalized_json)) as run:
            self.assertEqual([expected],transition._query_listeners(12345))
        args,kwargs=run.call_args
        command=args[0][-1]
        self.assertIn('Get-NetTCPConnection -LocalPort 12345 -State Listen',command)
        self.assertIn("Select-Object LocalPort,@{Name='State';Expression={$_.State.ToString()}},OwningProcess",command)
        self.assertEqual(5,kwargs['timeout']);self.assertTrue(kwargs['check'])
        self.assertEqual(['powershell.exe','-NoProfile','-NonInteractive','-Command'],args[0][:4])

        # The retained Windows PowerShell 5.1 result is intentionally rejected by
        # the strict Python schema; production now serializes this enum as text.
        with mock.patch.object(transition.subprocess,'run',return_value=Result(numeric_json)):
            with self.assertRaisesRegex(ValueError,'schema'): transition._query_listeners(12345)

        rows=[expected,{'LocalPort':12345,'State':'Listen','OwningProcess':12346}]
        with mock.patch.object(transition.subprocess,'run',return_value=Result(json.dumps(rows))):
            self.assertEqual(rows,transition._query_listeners(12345))
        with mock.patch.object(transition.subprocess,'run',return_value=Result('')):
            self.assertEqual([],transition._query_listeners(12345))
        for bad_json in ('{broken',
                '{"LocalPort":"8030","State":"Listen","OwningProcess":701}',
                '{"LocalPort":8030,"State":2,"OwningProcess":701}',
                '{"LocalPort":8030,"State":"Closed","OwningProcess":701}',
                '{"LocalPort":8030,"State":"Listen","OwningProcess":true}',
                '{"LocalPort":true,"State":"Listen","OwningProcess":701}'):
            with self.subTest(bad_json=bad_json), mock.patch.object(
                    transition.subprocess,'run',return_value=Result(bad_json)):
                with self.assertRaises((ValueError,json.JSONDecodeError)):
                    transition._query_listeners(12345)
        with mock.patch.object(transition.subprocess,'run') as run:
            for invalid_port in (True,False,0,65536,'8030'):
                with self.assertRaises(ValueError): transition._query_listeners(invalid_port)
            run.assert_not_called()

    def _direct_health_fixture(self):
        root=self.root/'direct-health-proof';root.mkdir(exist_ok=True)
        config=root/'coordinator.json';runtime=root/'runtime.py';python=root/'python.exe'
        config.write_bytes(b'fixture-config');runtime.write_bytes(b'fixture-runtime');python.write_bytes(b'fixture-python')
        health=root/'supervisor-health.json';token=root/'health.token'
        token.write_text('HEALTH_TOKEN_SENTINEL',encoding='utf-8')
        supervisor_id={'pid':101,'created':1001};child_id={'pid':202,'created':2002}
        scope_name='Local\\fixture-job';nonce='fixture-health-nonce'
        config_sha=sha(config);runtime_sha=sha(runtime);python_sha=sha(python)
        plan={'port':8030,'activation_nonce':nonce,'expected_config_sha256':config_sha,
            'expected_runtime_entry_sha256':runtime_sha,'expected_python_sha256':python_sha}
        values={'plan':plan,'config_path':config,'runtime_path':runtime,'python_path':python,
            'health_file':health,'health_token_path':token}
        receipt={'supervisor_identity':supervisor_id,'coordinator_identity':child_id,
            'scope_member':True,'ready_go':True,'scope_name':scope_name,'activation_nonce':nonce,
            'config_path':str(config.resolve()),'config_sha256':config_sha,
            'runtime_entry_path':str(runtime.resolve()),'runtime_entry_sha256':runtime_sha,
            'python_path':str(python.resolve()),'python_sha256':python_sha}
        supervisor={'identity_status':'verified','identity':supervisor_id,'activation_nonce':nonce,
            'config_path':str(config.resolve()),'config_sha256':config_sha,
            'runtime_entry_path':str(runtime.resolve()),'runtime_entry_sha256':runtime_sha,
            'python_path':str(python.resolve()),'python_sha256':python_sha}
        child={'coordinator_identity':child_id,'pid':child_id['pid'],
            'creation_identity':child_id,'scope_name':scope_name}
        health.write_text(json.dumps({'state':'running','supervisor':supervisor,'child':child}),encoding='utf-8')
        return values,receipt,child_id

    def test_health_timeout_reports_only_safe_last_listener_and_http_observations(self):
        values,receipt,child_id=self._direct_health_fixture()
        valid_listener=[{'LocalPort':8030,'State':'Listen','OwningProcess':child_id['pid']}]
        cases=(
            ('query_error',ValueError('SECRET_SENTINEL_QUERY_ERROR'),
             {'stage':'listener_query','code':'query_error','exception_type':'ValueError'}),
            ('no_listener',[],{'stage':'listener_query','code':'no_listener'}),
            ('http_401',valid_listener,{'stage':'health_http','code':'http_error','http_status':401}),
            ('connection_error',valid_listener,{'stage':'health_http','code':'connection_error','exception_type':'URLError'}),
        )
        for name,listener_result,expected in cases:
            with self.subTest(name=name):
                class FakeOpener:
                    def open(self,_request,timeout):
                        if name=='http_401':
                            raise transition.urllib.error.HTTPError(
                                'http://127.0.0.1:8030/v1/health',401,'SECRET_SENTINEL_HTTP',None,None)
                        if name=='connection_error':
                            raise transition.urllib.error.URLError('SECRET_SENTINEL_CONNECT')
                        raise AssertionError('opener must not be used for listener-only observations')
                listener_kwargs={'side_effect':listener_result} if isinstance(listener_result,Exception) else {'return_value':listener_result}
                with mock.patch.object(transition,'_query_listeners',**listener_kwargs), \
                     mock.patch.object(transition.gate_execution,'process_identity',return_value=child_id), \
                     mock.patch.object(transition.urllib.request,'build_opener',return_value=FakeOpener()), \
                     mock.patch.object(transition.time,'monotonic',side_effect=[0.0,0.0,2.0]), \
                     mock.patch.object(transition.time,'sleep'):
                    result=transition._health_proof(receipt,values,timeout=1)
                self.assertEqual(result['status'],'HELD')
                self.assertEqual(result['reason'],'bounded authenticated listener/health proof timed out')
                self.assertEqual(result['last_observation'],expected)
                serialized=json.dumps(result)
                for secret in ('SECRET_SENTINEL_QUERY_ERROR','SECRET_SENTINEL_HTTP',
                               'SECRET_SENTINEL_CONNECT','HEALTH_TOKEN_SENTINEL'):
                    self.assertNotIn(secret,serialized)

    def test_health_proof_preserves_explicit_held_for_authenticated_invalid_schema(self):
        values,receipt,child_id=self._direct_health_fixture()
        listener=[{'LocalPort':8030,'State':'Listen','OwningProcess':child_id['pid']}]
        class Response:
            status=200
            def __enter__(self): return self
            def __exit__(self,*args): return False
            def read(self,_size): return b'{"status":"paused","pending_events":0,"last_error":null}'
        class Opener:
            def open(self,_request,timeout): return Response()
        with mock.patch.object(transition,'_query_listeners',return_value=listener), \
             mock.patch.object(transition.gate_execution,'process_identity',return_value=child_id), \
             mock.patch.object(transition.urllib.request,'build_opener',return_value=Opener()), \
             mock.patch.object(transition.time,'monotonic',side_effect=[0.0,0.0]):
            result=transition._health_proof(receipt,values,timeout=1)
        self.assertEqual(result,{'status':'HELD',
            'reason':'coordinator health summary schema/state is not healthy'})


if __name__=='__main__': unittest.main()
