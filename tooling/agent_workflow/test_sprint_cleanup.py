import importlib.util
import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest import mock

RUNTIME=Path(__file__).with_name('workflow_runtime.py').resolve()
spec=importlib.util.spec_from_file_location('cleanup_runtime',RUNTIME)
wf=importlib.util.module_from_spec(spec); spec.loader.exec_module(wf)


class SprintCleanupTests(unittest.TestCase):
    def setUp(self):
        base=Path.home()/'AppData/Local/Temp/opencode'
        base.mkdir(parents=True,exist_ok=True)
        self.tmp=tempfile.TemporaryDirectory(dir=base,prefix='sprint-cleanup-')
        self.root=Path(self.tmp.name); self.repo=self.root/'author'; self.repo.mkdir()
        self.exchange=self.root/'work/agent_exchange'; (self.exchange/'candidates').mkdir(parents=True)
        self.reports=self.repo/'build/agent-workflow/sprint-a/reports'; self.reports.mkdir(parents=True)
        log=self.reports/'one-logs/1.log'; log.parent.mkdir(); log.write_text('owned gate output')
        receipt=self.reports/'one-gate-validation.json'
        wf.writejson(receipt,{'checks':[{'log':str(log),'log_sha256':wf.sha(log)}]})
        (self.repo/'build/agent-workflow/sprint-b/reports').mkdir(parents=True)
        (self.repo/'build/agent-workflow/sprint-b/reports/keep.json').write_text('other sprint')
        (self.exchange/'manual-tests.md').write_text('old')
        self.add_candidate('one','sprint-a'); self.add_candidate('two','sprint-b')
        self.add_candidate('stale','sprint-a',stale=True)
        self.add_candidate('same-name-other-author','sprint-a',repo=self.root/'other-author')
        self.add_candidate('legacy',None)
        selected=self.exchange/'candidates/one.json'; row=wf.readjson(selected)
        image=self.reports/'one/review/images/1.png'; image.parent.mkdir(parents=True); image.write_bytes(b'managed review copy')
        report=self.reports/'one-review.json'
        wf.writejson(report,{'candidate_id':'one','snapshot_key':'snap','reviewer':'review','decision':'needs_changes',
                             'findings':['review captured'],'viewed_images':[]})
        row['scoped_reviews']={'review':{'snapshot_key':'snap','report_path':str(report),'report_sha256':wf.sha(report),
                                         'original_images':[{'path':'one/review/images/1.png','sha256':wf.sha(image)}]}}
        wf.writejson(selected,row)
        (self.reports/'notes.txt').write_text('user-owned unexpected file')

    def tearDown(self): self.tmp.cleanup()

    def add_candidate(self,ident,sprint,stale=False,repo=None):
        owner=Path(repo or self.repo)
        row={'id':ident,'agent':'author','snapshot_key':'snap','worktree':{'worktree':str(owner)},'status':'candidate'}
        if sprint: row['sprint_id']=sprint
        if ident=='one':
            receipt=self.reports/'one-gate-validation.json'
            row['gate_validation']={'path':str(receipt),'sha256':wf.sha(receipt)}
        if stale: row.update({'base_sha':'0'*40,'base_ref':'gone','committed_changes':[],'dirty_changes':[]})
        wf.writejson(self.exchange/'candidates'/f'{ident}.json',row)

    def run_cleanup(self,apply=False,owner=''):
        args=Namespace(repo=str(self.repo),sprint='sprint-a',apply=apply,owner_reference=owner)
        with mock.patch('sys.stdout',new_callable=__import__('io').StringIO) as output:
            wf.cmd_sprint_cleanup(args,self.exchange)
            return json.loads(output.getvalue())

    def test_dry_run_is_read_only_and_apply_is_exact_sprint_scope(self):
        # POSIX may create ephemeral gate-validation.lock mutex files; exclude
        # those coordination artifacts while comparing user/report bytes.
        before={p:p.read_bytes() for root in (self.exchange,self.repo) for p in root.rglob('*') if p.is_file() and p.name!='gate-validation.lock'}
        preview=self.run_cleanup()
        after={p:p.read_bytes() for root in (self.exchange,self.repo) for p in root.rglob('*') if p.is_file() and p.name!='gate-validation.lock'}
        self.assertEqual(before,after); self.assertEqual(preview['candidate_ids'],['one','stale'])
        self.assertIn(str(self.reports/'notes.txt'),preview['preserved_paths'])
        self.assertIn(str(self.reports/'one-gate-validation.json'),preview['managed_files_removed'])
        self.assertIn(str(self.reports/'one/review/images/1.png'),preview['managed_files_removed'])
        self.assertIn(str(self.exchange/'candidates/stale.json'),preview['candidate_records_removed'])
        with self.assertRaisesRegex(ValueError,'owner-reference'):
            self.run_cleanup(apply=True)
        result=self.run_cleanup(apply=True,owner='owner explicitly closed sprint-a')
        self.assertTrue(result['applied']); self.assertFalse((self.exchange/'candidates/one.json').exists())
        self.assertTrue((self.exchange/'candidates/two.json').exists())
        self.assertTrue((self.exchange/'candidates/legacy.json').exists())
        self.assertTrue((self.reports/'notes.txt').exists())
        self.assertFalse((self.reports/'one-gate-validation.json').exists())
        self.assertFalse((self.reports/'one-logs/1.log').exists())
        self.assertFalse((self.reports/'one/review/images/1.png').exists())
        self.assertFalse((self.exchange/'candidates/stale.json').exists())
        self.assertTrue((self.exchange/'candidates/same-name-other-author.json').exists())
        self.assertTrue((self.repo/'build/agent-workflow/sprint-b/reports/keep.json').exists())
        self.assertNotEqual((self.exchange/'manual-tests.md').read_text(),'old')

    def test_pending_gate_and_scoped_review_block_without_mutation(self):
        path=self.exchange/'candidates/one.json'; c=wf.readjson(path)
        c['gate_request']={'snapshot_key':'snap','prompt_admitted':True}; wf.writejson(path,c)
        with self.assertRaisesRegex(ValueError,'gate request'):
            self.run_cleanup(apply=True,owner='closed')
        c.pop('gate_request'); c['scoped_reviews']={'review':{'snapshot_key':'snap','admission_unknown':True}}
        wf.writejson(path,c)
        with self.assertRaisesRegex(ValueError,'unfinished/stale scoped review'):
            self.run_cleanup(apply=True,owner='closed')
        self.assertTrue(path.exists()); self.assertTrue((self.reports/'one-gate-validation.json').exists())

    def test_quarantined_selected_candidate_preserves_candidate_and_log(self):
        path=self.exchange/'candidates/one.json'; candidate_before=path.read_bytes()
        log=self.reports/'one-logs/1.log'; log_before=log.read_bytes()
        blocker=self.exchange/'gate-blocker.json'
        wf.writejson(blocker,{'candidate_id':'one','pid':22,'log':str(log),'reason':'unverified stop'})
        blocker_before=blocker.read_bytes()
        with self.assertRaisesRegex(ValueError,'quarantined/unverified'):
            self.run_cleanup(apply=True,owner='closed')
        self.assertEqual(path.read_bytes(),candidate_before); self.assertEqual(log.read_bytes(),log_before)
        self.assertEqual(blocker.read_bytes(),blocker_before)

    def test_symlink_or_reparse_point_blocks_before_any_deletion(self):
        path=self.exchange/'candidates/one.json'; candidate_before=path.read_bytes()
        # Simulate an unprivileged Windows junction/reparse point check; the same
        # path scan uses Path.is_symlink and FILE_ATTRIBUTE_REPARSE_POINT in production.
        suspicious=self.reports/'linked-folder'
        original=Path.is_symlink
        def marked(item): return item==suspicious or original(item)
        with mock.patch.object(Path,'is_symlink',marked):
            suspicious.mkdir()
            try:
                with self.assertRaisesRegex(ValueError,'link/reparse point'):
                    self.run_cleanup(apply=True,owner='closed')
            finally: suspicious.rmdir()
        self.assertEqual(path.read_bytes(),candidate_before)
        self.assertTrue((self.reports/'one-gate-validation.json').exists())

    def test_cleanup_serializes_root_dispatch_and_sprint_scopes(self):
        acquired=[]
        class Handle:
            def close(self): pass
        def lock(path): acquired.append(Path(path)); return Handle()
        with mock.patch.object(wf,'gate_lock',side_effect=lock): self.run_cleanup()
        self.assertIn(self.exchange,acquired)
        self.assertIn(self.exchange/'dispatch-locks'/'one',acquired)
        self.assertTrue(any('sprint-locks' in str(p) for p in acquired))

    def test_submit_try_lock_reuses_shared_gate_mutex_nonblocking_mode(self):
        handle=object()
        with mock.patch.object(wf,'gate_lock',return_value=handle) as shared:
            self.assertIs(wf.try_sprint_lock(self.exchange,self.repo,'sprint-a'),handle)
        self.assertTrue(shared.call_args.kwargs['nonblocking'])
        with mock.patch.object(wf,'gate_lock',side_effect=TimeoutError('held')):
            with self.assertRaisesRegex(ValueError,'in progress'):
                wf.try_sprint_lock(self.exchange,self.repo,'sprint-a')


if __name__=='__main__': unittest.main()
