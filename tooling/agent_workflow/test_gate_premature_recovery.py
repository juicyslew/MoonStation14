import base64
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tooling.agent_workflow import gate_liveness as live


class Lock:
    def close(self): pass


class Workflow:
    def __init__(self, root): self.root = root
    @staticmethod
    def validid(value): return value
    @staticmethod
    def gate_lock(*args, **kwargs): return Lock()
    @staticmethod
    def sprint_lock(*args, **kwargs): return Lock()
    @staticmethod
    def readjson(path): return json.loads(Path(path).read_text())
    @staticmethod
    def writejson(path, value): Path(path).write_text(json.dumps(value, sort_keys=True))
    @staticmethod
    def sha(path):
        import hashlib
        return hashlib.sha256(Path(path).read_bytes()).hexdigest()
    @staticmethod
    def snapshot_key(value):
        import hashlib
        return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    @staticmethod
    def verify_gate(candidate): return None, candidate['gate_plan']
    @staticmethod
    def verify_candidate(candidate): return candidate
    @staticmethod
    def verify_gate_receipt(candidate, require_pass): return Path(candidate['receipt_path']), {}
    @staticmethod
    def read_report(path): return json.loads(Path(path).read_text())


class PrematureRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name); (self.root/'candidates').mkdir()
        self.report_path = self.root/'report.json'; self.receipt_path = self.root/'receipt.json'
        report = {'candidate_id':'r31','snapshot_key':'snap','reviewer':'integration-lead','decision':'needs_changes',
                  'findings':['pending validation'],'evidence_summary':{'status':'NOT_RUN'}}
        self.report_path.write_text(json.dumps(report)); self.receipt_path.write_text('{"status":"FAIL"}')
        self.report_sha = Workflow.sha(self.report_path); self.receipt_sha = Workflow.sha(self.receipt_path)
        self.candidate = {'id':'r31','snapshot_key':'snap','sprint_id':'sprint','worktree':{'worktree':str(self.root)},
            'gate_plan':{'checks':['test']}, 'gate_request':{'prompt_admitted':True,'dispatch_state':'admitted',
                'requested_at':1,'snapshot_key':'snap','session_id':'lead','prompt_body':{'text':'unchanged'}},
            'review':{'result':'needs_changes','report_path':str(self.report_path),'report_sha256':self.report_sha},
            'gate_outcome':{'result':'needs_changes','report_sha256':self.report_sha},
            'author_completion':{'status':'delivered','body':{'text':'saved'}}, 'scoped_reviews':{'a':{'report_sha256':'keep'}},
            'receipt_path':str(self.receipt_path)}
        Workflow.writejson(self.root/'candidates'/'r31.json', self.candidate)
        live._write(live._path(self.root,'r31'), {'candidate_id':'r31','snapshot_key':'snap','session_id':'lead','state':'finished','recovery_count':2,
            'author_completion_delivery':{'body':{'id':'old-message','text':'old completion'},'execution_observed_at':123},
            'completion_delivery_pending':{'id':'old-message'},'completion_retry_error':'old error','completion_retry_at':124,
            'unrelated_scoped_marker':{'value':'keep'}})
        self.job = {'state':'completed','candidate_id':'r31','job_id':'r31','snapshot_key':'snap','lead_session_id':'lead',
                    'plan_digest':Workflow.snapshot_key(self.candidate['gate_plan']),
                    'receipt':{'path':str(self.receipt_path),'sha256':self.receipt_sha}}
        self.wf = Workflow(self.root)

    def call_repair(self, **kw):
        args = dict(expected_snapshot='snap', expected_lead_session_id='lead', expected_report_sha256=self.report_sha,
                    expected_receipt_sha256=self.receipt_sha)
        args.update(kw)
        with patch('tooling.agent_workflow.gate_execution.read', return_value=self.job):
            return live.recover_premature_outcome(self.root,'r31',workflow=self.wf,**args)

    def test_repairs_and_archives_exact_bodies_and_repeat_is_read_only(self):
        raw = self.report_path.read_bytes(); result = self.call_repair()
        self.assertEqual(result['status'],'recovered')
        archive = json.loads(Path(result['archive_path']).read_text())
        self.assertEqual(base64.b64decode(archive['report']['bytes_base64']),raw)
        self.assertEqual(archive['candidate_before']['author_completion'],self.candidate['author_completion'])
        self.assertEqual(archive['health_before']['author_completion_delivery'],{'body':{'id':'old-message','text':'old completion'},'execution_observed_at':123})
        after = Workflow.readjson(self.root/'candidates'/'r31.json')
        for key in ('gate_outcome','review','author_completion'): self.assertNotIn(key,after)
        for key in ('gate_request','scoped_reviews'): self.assertEqual(after[key],self.candidate[key])
        health=live.read_health(self.root,'r31')
        self.assertNotIn('author_completion_delivery',health)
        for key in ('completion_delivery_pending','completion_retry_error','completion_retry_at'): self.assertNotIn(key,health)
        self.assertEqual(health['recovery_count'],2); self.assertEqual(health['unrelated_scoped_marker'],{'value':'keep'})
        # The old observation cannot satisfy the new completion event: new ID is independently seen and steered.
        calls=[]
        def api(server,method,path,body=None,timeout=15):
            calls.append((method,path,body))
            if method=='GET' and path=='/api/session/active': return 200,{'data':{}}
            if method=='GET' and path.endswith('/inbox'): return 200,{'data':[{'id':'new-message','delivery':'queue'}]}
            if method=='PATCH':
                self.assertTrue(path.endswith('/new-message')); return 204,{}
            raise AssertionError((method,path,body))
        self.assertIsNone(live._observe_author_delivery(self.root,after,health,{'id':'new-message','origin_session_id':'author'},'author_completion_delivery',api,200))
        self.assertTrue(any(x[0]=='PATCH' and x[1].endswith('/new-message') for x in calls))
        self.assertTrue(live.read_health(self.root,'r31')['author_completion_delivery']['wake_attempted'])
        archive_bytes = Path(result['archive_path']).read_bytes(); candidate_bytes = (self.root/'candidates'/'r31.json').read_bytes()
        self.assertEqual(self.call_repair()['status'],'already_recovered')
        self.assertEqual(Path(result['archive_path']).read_bytes(),archive_bytes)
        self.assertEqual((self.root/'candidates'/'r31.json').read_bytes(),candidate_bytes)

    def test_rejects_mismatches_stale_and_valid_review_without_mutation(self):
        original = (self.root/'candidates'/'r31.json').read_bytes()
        for kw in ({'expected_snapshot':'wrong'}, {'expected_lead_session_id':'wrong'},
                   {'expected_report_sha256':'0'*64}, {'expected_receipt_sha256':'0'*64}):
            with self.assertRaises(ValueError): self.call_repair(**kw)
            self.assertEqual((self.root/'candidates'/'r31.json').read_bytes(),original)
        with patch.object(self.wf,'verify_gate',side_effect=ValueError('stale')):
            with self.assertRaisesRegex(ValueError,'stale'): self.call_repair()
        live._write(live._path(self.root,'r31'), {'candidate_id':'r31','snapshot_key':'snap','session_id':'wrong','state':'finished'})
        with self.assertRaisesRegex(ValueError,'health'):
            self.call_repair()
        live._write(live._path(self.root,'r31'), {'candidate_id':'r31','snapshot_key':'snap','session_id':'lead','state':'finished'})
        report = json.loads(self.report_path.read_text()); report['evidence_summary']={'status':'PASS'}
        self.report_path.write_text(json.dumps(report)); self.candidate['review']['report_sha256'] = Workflow.sha(self.report_path)
        self.candidate['gate_outcome']['report_sha256'] = self.candidate['review']['report_sha256']
        Workflow.writejson(self.root/'candidates'/'r31.json',self.candidate)
        with patch('tooling.agent_workflow.gate_execution.read',return_value=self.job):
            with self.assertRaises(ValueError): live.recover_premature_outcome(self.root,'r31',expected_snapshot='snap',
                expected_lead_session_id='lead',expected_report_sha256=self.candidate['review']['report_sha256'],
                expected_receipt_sha256=self.receipt_sha,workflow=self.wf)

    def test_wrong_reviewer_role_rejected_even_when_session_binding_is_correct(self):
        report = json.loads(self.report_path.read_text()); report['reviewer'] = 'lead'
        self.report_path.write_text(json.dumps(report))
        self.candidate['review']['report_sha256'] = Workflow.sha(self.report_path)
        self.candidate['gate_outcome']['report_sha256'] = self.candidate['review']['report_sha256']
        Workflow.writejson(self.root/'candidates'/'r31.json',self.candidate)
        with patch('tooling.agent_workflow.gate_execution.read',return_value=self.job):
            with self.assertRaisesRegex(ValueError,'report identity'):
                live.recover_premature_outcome(self.root,'r31',expected_snapshot='snap',expected_lead_session_id='lead',
                    expected_report_sha256=self.candidate['review']['report_sha256'],expected_receipt_sha256=self.receipt_sha,workflow=self.wf)

    def test_retries_after_archive_and_health_or_candidate_write_failure(self):
        original_write = live._write
        original_candidate_write = self.wf.writejson
        for failure in ('health', 'candidate'):
            with self.subTest(failure=failure):
                # Each scenario starts from the exact same premature state.
                self.setUp()
                archive_dir = self.root/'gate-recovery'
                if failure == 'health':
                    fired = {'value':False}
                    def fail_health(path, value):
                        if Path(path) == live._path(self.root,'r31') and not fired['value']:
                            fired['value'] = True; raise OSError('injected health write failure')
                        return original_write(path,value)
                    ctx = patch.object(live,'_write',side_effect=fail_health)
                else:
                    fired = {'value':False}
                    def fail_candidate(path, value):
                        if not fired['value']:
                            fired['value'] = True; raise OSError('injected candidate write failure')
                        return original_candidate_write(path,value)
                    ctx = patch.object(self.wf,'writejson',side_effect=fail_candidate)
                with patch('tooling.agent_workflow.gate_execution.read',return_value=self.job), ctx:
                    with self.assertRaisesRegex(OSError,'injected'):
                        live.recover_premature_outcome(self.root,'r31',expected_snapshot='snap',expected_lead_session_id='lead',
                            expected_report_sha256=self.report_sha,expected_receipt_sha256=self.receipt_sha,workflow=self.wf)
                archive = next(archive_dir.glob('*.json')); archive_bytes = archive.read_bytes()
                candidate_before_retry = Workflow.readjson(self.root/'candidates'/'r31.json')
                self.assertEqual(candidate_before_retry,self.candidate)
                calls=[]
                def no_native(*args,**kwargs): calls.append((args,kwargs)); raise AssertionError('native operation invoked')
                with patch('tooling.agent_workflow.gate_execution.read',return_value=self.job), \
                     patch('tooling.agent_workflow.gate_execution.request',side_effect=no_native):
                    result=live.recover_premature_outcome(self.root,'r31',expected_snapshot='snap',expected_lead_session_id='lead',
                        expected_report_sha256=self.report_sha,expected_receipt_sha256=self.receipt_sha,workflow=self.wf)
                self.assertEqual(result['status'],'recovered'); self.assertEqual(archive.read_bytes(),archive_bytes)
                self.assertEqual(len(list(archive_dir.glob('*.json'))),1); self.assertEqual(calls,[])
                after=Workflow.readjson(self.root/'candidates'/'r31.json')
                for key in ('gate_request','scoped_reviews','receipt_path'): self.assertEqual(after[key],self.candidate[key])
                health=live.read_health(self.root,'r31')
                self.assertEqual(health['recovery_count'],2); self.assertEqual(health['unrelated_scoped_marker'],{'value':'keep'})


if __name__ == '__main__': unittest.main()
