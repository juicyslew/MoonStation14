import tempfile
import unittest
from pathlib import Path
import importlib.util
from unittest import mock
import subprocess
import time
from types import SimpleNamespace
from argparse import Namespace

BASE = Path(__file__).parent
def load(name):
    spec = importlib.util.spec_from_file_location(name, BASE / f'{name}.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module); return module
w = load('workflow_runtime')
oa = load('owner_attention')


class OwnerAttentionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'candidates').mkdir()

    def tearDown(self): self.tmp.cleanup()

    def candidate(self, ident, sprint='s1', seq=1):
        c = {'id': ident, 'sprint_id': sprint, 'revision_sequence': seq, 'submitted_at': seq,
             'snapshot_key': ident + '-snap', 'worktree': {'worktree': str(self.root / 'repo')},
             'gate_plan': {'title': ident, 'fixture': 'test.py', 'manual_steps': ['step']},
             'gate_outcome': {'result': 'ready_for_owner'}, 'owner_review': {
         'state': 'awaiting_owner', 'candidate_id': ident, 'snapshot_key': ident+'-snap',
                 'requested_at': 10, 'origin_session_id': 'session',
                 'readiness': {'checked_at':time.time(),'eligible':True,'reason':None,'snapshot_key':ident+'-snap','review_sha256':None,'validation_sha256':None}}}
        w.writejson(self.root / 'candidates' / f'{ident}.json', c)
        return c

    def test_feedback_changes_responsibility_not_verdict_and_requires_new_prompt(self):
        c = self.candidate('one')
        evidence = {'type':'user','id':'m1','text':'Please adjust this','metadata':{},'time':11,'session_id':'session'}
        result = oa.acknowledge_feedback(self.root, 'session', 'm1', 11, workflow=w, evidence=evidence)
        self.assertEqual('acknowledged', result['status'])
        saved = w.readjson(self.root/'candidates'/'one.json')
        self.assertEqual('ready_for_owner', saved['gate_outcome']['result'])
        self.assertNotIn('owner_decision', saved)
        self.assertEqual('feedback_received', saved['owner_review']['state'])
        self.assertEqual('owner responded; agent triage required', oa.collect(self.root)['agent_work'][0]['reason'])
        self.assertEqual('ignored', oa.acknowledge_feedback(self.root, 'session', 'm1', 11, workflow=w, evidence=evidence)['status'])

    def test_filters_synthetic_pre_request_and_ambiguous(self):
        self.candidate('one'); self.candidate('two', 's2')
        e = {'type':'user','id':'m','text':'feedback','metadata':{},'time':11,'session_id':'session'}
        synthetic = {**e, 'metadata':{'source':{'workflow_gate_completion':True}}}
        self.assertEqual('ignored', oa.acknowledge_feedback(self.root,'session','m',11,workflow=w,evidence=synthetic)['status'])
        self.assertEqual('ignored', oa.acknowledge_feedback(self.root,'session','m',9,workflow=w,evidence={**e,'time':9})['status'])
        self.assertEqual('ambiguous', oa.acknowledge_feedback(self.root,'session','m',11,workflow=w,evidence=e)['status'])

    def test_collect_only_current_revision_and_legacy_not_invented(self):
        old = self.candidate('old', seq=1); self.candidate('new', seq=2)
        self.assertEqual(['new'], [x['id'] for x in oa.collect(self.root)['awaiting_owner']])
        old.pop('revision_sequence'); old.pop('submitted_at'); old.pop('owner_review')
        old['id']='legacy'; old['gate_outcome']={'result':'ready_for_owner'}
        w.writejson(self.root/'candidates'/'legacy.json',old)
        legacy = oa.collect(self.root)['unconfirmed']
        self.assertEqual([], legacy) # Known sequenced head supersedes legacy rows.
        # Without any sequence metadata, collapse historical rows into one reconciliation summary.
        w.readjson(self.root/'candidates'/'new.json').pop('revision_sequence')
        newer = w.readjson(self.root/'candidates'/'new.json'); newer.pop('revision_sequence'); newer.pop('submitted_at')
        w.writejson(self.root/'candidates'/'new.json', newer)
        prior = w.readjson(self.root/'candidates'/'old.json'); prior.pop('revision_sequence'); prior.pop('submitted_at')
        w.writejson(self.root/'candidates'/'old.json', prior)
        legacy = oa.collect(self.root)['unconfirmed']
        self.assertEqual({'legacy','new','old'}, set(legacy[0]['candidate_ids']))

    def test_superseded_request_cannot_acknowledge_and_projects_have_independent_heads(self):
        self.candidate('old', seq=1)
        self.candidate('new', seq=2)
        other = self.candidate('other-project', sprint='s2', seq=1)
        ev = {'type':'user','id':'msg','text':'feedback','metadata':{},'time':11,'session_id':'session'}
        result = oa.acknowledge_feedback(self.root,'session','msg',11,workflow=w,evidence=ev)
        self.assertEqual('ambiguous', result['status'])
        ev['candidate_id'] = 'old'
        self.assertEqual('ignored', oa.acknowledge_feedback(self.root,'session','msg',11,workflow=w,evidence=ev)['status'])
        found = oa.collect(self.root)
        self.assertEqual({'new','other-project'}, {x['id'] for x in found['awaiting_owner']})

    def test_stale_refresh_is_agent_work_and_owner_registration_is_strict_idempotent(self):
        c = self.candidate('ready')
        class Workflow:
            readjson = staticmethod(w.readjson); writejson = staticmethod(w.writejson)
            sprint_lock = staticmethod(w.sprint_lock)
            def verify_gate(self, c): raise ValueError('stale source')
            def verify_publication(self, c, root): pass
        failed = oa.collect(self.root, workflow=Workflow(), refresh=True)
        self.assertFalse(failed['awaiting_owner'])
        self.assertIn('stale source', failed['agent_work'][0]['reason'])
        self.assertTrue(failed['agent_work'][0]['reason'].startswith('readiness evidence stale'))
        self.assertFalse(failed['awaiting_owner'])
        good = mock.Mock()
        good.readjson = w.readjson; good.writejson = w.writejson; good.sprint_lock = w.sprint_lock
        good.verify_gate.return_value = c; good.verify_publication.return_value = None
        w.writejson(self.root/'candidates'/'ready.json', {**c, 'owner_review':{}})
        oa.register_request(self.root,c,0,'session',workflow=good)
        saved = w.readjson(self.root/'candidates'/'ready.json')
        self.assertEqual(0, saved['owner_review']['requested_at'])
        saved['owner_review']['state'] = 'feedback_received'; w.writejson(self.root/'candidates'/'ready.json',saved)
        oa.register_request(self.root,c,20,'session',workflow=good)
        self.assertEqual('feedback_received', w.readjson(self.root/'candidates'/'ready.json')['owner_review']['state'])
        oa.register_request(self.root,c,30,'session',workflow=good,after_feedback_message_id='ack-1')
        # The request path is only available when the exact receipt was observed.
        saved = w.readjson(self.root/'candidates'/'ready.json')
        saved['owner_review']['feedback']={'message_id':'ack-1','at':25,'session_id':'session'}
        saved['owner_review']['state']='feedback_received'; w.writejson(self.root/'candidates'/'ready.json',saved)
        oa.register_request(self.root,c,30,'session',workflow=good,after_feedback_message_id='ack-1')
        self.assertEqual('awaiting_owner', w.readjson(self.root/'candidates'/'ready.json')['owner_review']['state'])

    def test_negative_readiness_persists_and_expiry_never_claims_owner(self):
        self.candidate('cached')
        class Stale:
            readjson = staticmethod(w.readjson); writejson = staticmethod(w.writejson); sprint_lock = staticmethod(w.sprint_lock)
            def verify_gate(self, c): raise ValueError('stale')
            verify_publication = staticmethod(lambda c, root: None)
        oa.collect(self.root, workflow=Stale(), refresh=True)
        self.assertFalse(oa.collect(self.root)['awaiting_owner'])
        saved = w.readjson(self.root/'candidates'/'cached.json')
        self.assertIn('stale', saved['owner_review']['readiness']['reason'])
        saved['owner_review']['readiness']['checked_at'] = 0
        w.writejson(self.root/'candidates'/'cached.json', saved)
        self.assertFalse(oa.collect(self.root)['awaiting_owner'])
        self.assertIn('expired', oa.collect(self.root)['unconfirmed'][0]['reason'])

    def test_feedback_during_refresh_is_not_clobbered(self):
        self.candidate('race')
        class Racing:
            readjson = staticmethod(w.readjson); writejson = staticmethod(w.writejson); sprint_lock = staticmethod(w.sprint_lock)
            def verify_gate(self, c):
                path = self_outer.root/'candidates'/'race.json'; saved=w.readjson(path)
                saved['owner_review']['state']='feedback_received'; w.writejson(path,saved)
                raise ValueError('stale')
            verify_publication = staticmethod(lambda c, root: None)
        self_outer = self
        oa.collect(self.root, workflow=Racing(), refresh=True)
        self.assertEqual('feedback_received', w.readjson(self.root/'candidates'/'race.json')['owner_review']['state'])

    def test_isolated_installed_module_load_and_observer_helper(self):
        import shutil
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            shutil.copy(Path(oa.__file__), folder/'owner_attention.py')
            shutil.copy(Path(w.__file__), folder/'workflow_runtime.py')
            (folder/'owner_feedback.py').write_text('def ensure_started(root): return {"status":"started","root":str(root)}\n')
            spec = importlib.util.spec_from_file_location('installed_owner_attention', folder/'owner_attention.py')
            installed = importlib.util.module_from_spec(spec); spec.loader.exec_module(installed)
            import sys
            sys.path.insert(0, str(folder))
            # Suite-wide import leaves bare owner_feedback cached; resolve this
            # installed-layout helper against its intended fixture owner.
            fake_feedback = SimpleNamespace(ensure_started=lambda root: {'status':'started','root':str(root)})
            try:
                with mock.patch.dict('sys.modules', {'owner_feedback': fake_feedback}):
                    result = installed.ensure_feedback_observer(folder)
            finally: sys.path.remove(str(folder))
            self.assertEqual('started', result['status'])

    def test_runtime_observer_start_is_best_effort_and_records_health(self):
        import sys, types
        c=self.candidate('observer')
        path=self.root/'candidates'/'observer.json'
        fake=types.SimpleNamespace(ensure_started=lambda root:{'status':'started','root':str(root)})
        previous=sys.modules.get('owner_feedback'); sys.modules['owner_feedback']=fake
        try: result=w._ensure_owner_feedback(self.root,path,c)
        finally:
            if previous is None: sys.modules.pop('owner_feedback',None)
            else: sys.modules['owner_feedback']=previous
        self.assertEqual('started',result['status'])
        self.assertEqual('started',w.readjson(path)['owner_review']['observer']['status'])

    def test_import_prefers_runtime_over_poison_source_shim_and_accepts_installed_workflow_payload(self):
        import shutil, sys
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp); shutil.copy(Path(oa.__file__),folder/'owner_attention.py')
            shutil.copy(Path(w.__file__),folder/'workflow_runtime.py')
            (folder/'workflow.py').write_text("raise RuntimeError('source shim executed')\n")
            sys.modules.pop('_moonstation14_owner_attention_runtime',None)
            spec=importlib.util.spec_from_file_location('isolated_source_owner',folder/'owner_attention.py')
            source=importlib.util.module_from_spec(spec); spec.loader.exec_module(source)
            self.assertTrue(hasattr(source._load_workflow(),'verify_gate'))
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp); shutil.copy(Path(oa.__file__),folder/'owner_attention.py')
            shutil.copy(Path(w.__file__),folder/'workflow.py')
            sys.modules.pop('_moonstation14_owner_attention_runtime',None)
            spec=importlib.util.spec_from_file_location('isolated_installed_owner',folder/'owner_attention.py')
            installed=importlib.util.module_from_spec(spec); spec.loader.exec_module(installed)
            self.assertTrue(hasattr(installed._load_workflow(),'verify_gate'))

    def test_explicit_legacy_head_registration_collects_and_acknowledges_until_new_head(self):
        c=self.candidate('legacy'); c.pop('revision_sequence'); c.pop('submitted_at')
        c['owner_review']={'state':'unconfirmed'}; w.writejson(self.root/'candidates'/'legacy.json',c)
        class Good:
            readjson=staticmethod(w.readjson); writejson=staticmethod(w.writejson); sprint_lock=staticmethod(w.sprint_lock)
            def verify_gate(self,c): return c
            def verify_publication(self,c,root): return None
        with self.assertRaisesRegex(ValueError,'explicit observer-bound'):
            oa.register_request(self.root,c,10,'session',workflow=Good())
        proof={'verified_by':'owner_feedback_observer','candidate_id':'legacy','snapshot_key':'legacy-snap',
               'origin_session_id':'session','requested_at':10,'announcement_message_id':'notice'}
        oa.register_request(self.root,c,10,'session',workflow=Good(),legacy_head=proof)
        self.assertEqual(['legacy'],[x['id'] for x in oa.collect(self.root)['awaiting_owner']])
        ev={'type':'user','id':'msg','text':'feedback','metadata':{},'time':11,'session_id':'session'}
        self.assertEqual('acknowledged',oa.acknowledge_feedback(self.root,'session','msg',11,workflow=w,evidence=ev)['status'])
        newer=self.candidate('new-head',seq=1)
        newer['owner_review']['requested_at']=13
        w.writejson(self.root/'candidates'/'new-head.json',newer)
        self.assertEqual(['new-head'],[x['id'] for x in oa.collect(self.root)['awaiting_owner']])
        self.assertEqual('ignored',oa.acknowledge_feedback(self.root,'session','msg2',12,workflow=w,evidence={**ev,'id':'msg2','time':12})['status'])

    def test_real_submission_sequences_supersede_same_work_but_not_other_sprints(self):
        repo = self.root/'repo'; repo.mkdir(); (repo/'seed.txt').write_text('seed')
        def git(*args): return subprocess.run(['git','-C',str(repo),*args],check=True,capture_output=True).stdout
        git('init','-q'); git('config','user.email','test@example.invalid'); git('config','user.name','test')
        git('add','.'); git('commit','-qm','seed'); git('branch','-M','main')
        queue = self.root/'queue'; (queue/'candidates').mkdir(parents=True)
        def submit(ident,sprint):
            a=Namespace(id=ident, agent='agent', visual_review_required=False, base_ref='main', evidence=[],
                        build_identity='fixture', steps='steps', unverified='', gate_plan=None)
            w._cmd_submit_locked(a,queue,ident,repo,sprint)
            return w.readjson(queue/'candidates'/f'{ident}.json')
        first=submit('one','s1'); second=submit('two','s1'); other=submit('project-two','s2')
        self.assertEqual((1,2,1),(first['revision_sequence'],second['revision_sequence'],other['revision_sequence']))
        self.assertEqual(['one'],second['supersedes'])
        self.assertEqual([],other['supersedes'])

    def test_registration_rejects_wrong_snapshot_verdict_and_owner_disposition(self):
        c = self.candidate('bad')
        class Good:
            readjson = staticmethod(w.readjson); writejson = staticmethod(w.writejson); sprint_lock = staticmethod(w.sprint_lock)
            def verify_gate(self, c): return c
            def verify_publication(self, c, root): pass
        for changed, message in [({'snapshot_key':'wrong'}, 'candidate changed'),
                                 ({'gate_outcome':{'result':'needs_changes'}}, 'ready_for_owner'),
                                 ({'owner_decision':{'decision':'accepted'}}, 'disposition')]:
            actual = {**c, **changed}; w.writejson(self.root/'candidates'/'bad.json',actual)
            with self.assertRaisesRegex(ValueError,message): oa.register_request(self.root,c,12,'session',workflow=Good())

    def test_explicit_acceptance_is_completed_not_agent_work(self):
        c = self.candidate('accepted')
        c['owner_decision']={'decision':'accepted'}
        w.writejson(self.root/'candidates'/'accepted.json',c)
        result=oa.collect(self.root)
        self.assertEqual(['accepted'],[x['id'] for x in result['completed']])
        self.assertFalse(result['agent_work'])

    def test_pending_new_revision_is_canonical_head_and_shows_agent_work(self):
        self.candidate('old-ready',seq=1)
        pending=self.candidate('new-pending',seq=2)
        pending.pop('owner_review'); pending['gate_outcome']={}
        w.writejson(self.root/'candidates'/'new-pending.json',pending)
        selected=oa.current_work(self.root)
        self.assertEqual('new-pending',selected[0]['head']['id'])
        result=oa.collect(self.root)
        self.assertFalse(result['awaiting_owner'])
        self.assertEqual('new-pending',result['agent_work'][0]['id'])
        self.assertIn('author / integration',result['agent_work'][0]['reason'])


if __name__ == '__main__': unittest.main()
