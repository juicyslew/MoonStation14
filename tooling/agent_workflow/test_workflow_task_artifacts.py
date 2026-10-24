import hashlib
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import workflow_task_artifacts as artifacts
import workflow_coordinator


class ArtifactExcerptTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name)
        self.task='task-a'; self.run='run-a'; self.digest='d'*64

    def tearDown(self): self.temp.cleanup()

    def save(self, text):
        raw=text.encode(); sha=hashlib.sha256(raw).hexdigest()
        path=self.root/'immutable'/self.task/('log-'+sha); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        return {'artifact_id':'art-a','run_id':self.run,'producer_task_id':self.task,'producer_id':self.task,
            'producer_kind':'TASK','type':'LOG','path':str(path),'sha256':sha,'size_bytes':len(raw),
            'immutable':True,'input_digest':self.digest}

    def read(self, item, **kwargs):
        return artifacts.read_log_excerpt(item,artifact_dir=self.root,task_id=self.task,run_id=self.run,
            input_digest=self.digest,**kwargs)

    def test_redacts_before_tail_and_preserves_validation_error(self):
        item=self.save('x'*9000+'\nAuthorization: Bearer tail-secret\ncompiler error: expected token\n')
        result=self.read(item,secret_values=['mapped-sentinel','tail-secret'])
        self.assertTrue(result['available']); self.assertTrue(result['truncated'])
        self.assertNotIn('tail-secret',result['text']); self.assertIn('compiler error: expected token',result['text'])

    def test_forged_metadata_path_missing_and_hash_fail_closed(self):
        item=self.save('compiler error')
        for field,value in [('type','PROVENANCE'),('producer_id','foreign'),('input_digest','foreign')]:
            forged=dict(item); forged[field]=value
            self.assertFalse(self.read(forged)['available'])
        forged=dict(item,path=str(self.root/'other'))
        self.assertEqual(self.read(forged)['reason'],'path_mismatch')
        item['sha256']='0'*64; item['path']=str(self.root/'immutable'/self.task/('log-'+'0'*64))
        self.assertEqual(self.read(item)['reason'],'missing')

    def test_oversize_rejected_before_open_and_size_mismatch(self):
        item=self.save('small'); item['size_bytes']=16*1024*1024+1
        unavailable=self.read(item)
        self.assertEqual(unavailable['reason'],'oversized'); self.assertEqual(unavailable['artifact_id'],'art-a')
        self.assertEqual(unavailable['sha256'],item['sha256']); self.assertEqual(unavailable['size_bytes'],item['size_bytes'])
        item=self.save('small'); item['size_bytes']+=1
        self.assertEqual(self.read(item)['reason'],'integrity_mismatch')
        item=self.save('same-size')
        Path(item['path']).write_bytes(b'changed!')
        self.assertEqual(self.read(item)['reason'],'integrity_mismatch')

    def test_reparse_fence_failure_is_typed_unavailable(self):
        from unittest import mock
        item=self.save('ordinary error')
        with mock.patch('workflow_coordinator_package.assert_no_reparse',side_effect=ValueError('fixture fence')):
            self.assertEqual(self.read(item)['reason'],'unsafe_path')

    def test_symlink_artifact_root_fails_closed_when_platform_allows(self):
        item=self.save('must not read through linked root')
        alias=self.root/'linked-root'
        try: alias.symlink_to(self.root,target_is_directory=True)
        except (OSError,NotImplementedError): self.skipTest('directory symlink privilege unavailable')
        item['path']=str(alias/'immutable'/self.task/('log-'+item['sha256']))
        result=artifacts.read_log_excerpt(item,artifact_dir=alias,task_id=self.task,run_id=self.run,input_digest=self.digest)
        self.assertEqual(result['reason'],'unsafe_path')

    def test_coordinator_keeps_last_four_logs_and_reports_omissions_and_error(self):
        saved={}; refs=[]
        for i in range(4):
            task_id=self.task; text=f'passing check {i}'
            raw=text.encode(); sha=hashlib.sha256(raw).hexdigest(); path=self.root/'immutable'/task_id/('log-'+sha)
            path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
            saved[f'art-{i}']={'artifact':{'artifact_id':f'art-{i}','run_id':self.run,'producer_task_id':task_id,
                'producer_id':task_id,'producer_kind':'TASK','type':'LOG','path':str(path),'sha256':sha,
                'size_bytes':len(raw),'immutable':True},'input_digest':self.digest}
            refs.append(f'art-{i}')
        final_id='task-final'; raw=b'final validation compiler failure'; sha=hashlib.sha256(raw).hexdigest()
        path=self.root/'immutable'/final_id/('log-'+sha); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        saved['art-final']={'artifact':{'artifact_id':'art-final','run_id':self.run,'producer_task_id':final_id,
            'producer_id':final_id,'producer_kind':'TASK','type':'LOG','path':str(path),'sha256':sha,
            'size_bytes':len(raw),'immutable':True},'input_digest':self.digest}
        class Store:
            def list_tasks(inner,run,generation):
                return [{'task_id':self.task,'kind':'CHECK','state':'COMPLETED','result':{'decision':'PASS','findings':[],
                    'artifact_refs':refs},'error_report':None},
                    {'task_id':final_id,'kind':'CHECK','state':'COMPLETED','result':{'decision':'FAIL','findings':[{'message':'final failure'}],
                    'artifact_refs':['art-final']},'error_report':None},
                    {'task_id':'error-task','kind':'CHECK','state':'COMPLETED','result':{'decision':'ERROR','findings':[],
                    'artifact_refs':[]}}]
            def get_artifact(inner,ref): return saved.get(ref)
            def get_task_incident(inner,task_id): return None
        coordinator=SimpleNamespace(store=Store(),artifact_dir=self.root,bindings={},author_worktrees={},review_bindings={},owner_bindings={})
        result=workflow_coordinator.ExplicitCoordinator._author_diagnostics(coordinator,{'run_id':self.run,
            'current_attempt':1,'input_digest':self.digest})
        self.assertEqual(result['skipped_logs'],1)
        self.assertEqual([x['artifact_id'] for x in result['logs']],['art-1','art-2','art-3','art-final'])
        self.assertIn('final validation compiler failure',result['logs'][-1]['text'])
        self.assertIsNone(result['checks'][2]['error_report'])

    def test_no_completed_check_is_pending_and_empty(self):
        coordinator=SimpleNamespace(store=SimpleNamespace(list_tasks=lambda *_:[{'kind':'CHECK','state':'RUNNING'}]),
            artifact_dir=self.root,bindings={},author_worktrees={},review_bindings={},owner_bindings={})
        result=workflow_coordinator.ExplicitCoordinator._author_diagnostics(coordinator,{'run_id':self.run,'current_attempt':2,'input_digest':self.digest})
        self.assertEqual(result['state'],'pending'); self.assertEqual(result['logs'],[])

    def test_diagnostics_have_finite_utf8_json_budget_and_skip_count(self):
        tasks=[{'task_id':f'check-{i}','kind':'CHECK','state':'COMPLETED','result':{'decision':'FAIL',
            'findings':[{'code':'é😀\x01'*300,'message':'credential=secret ' * 300} for _ in range(8)],'artifact_refs':[]}}
            for i in range(12)]
        class Store:
            def list_tasks(inner,*_): return tasks
            def get_artifact(inner,*_): return None
        coordinator=SimpleNamespace(store=Store(),artifact_dir=self.root,bindings={},author_worktrees={},review_bindings={},owner_bindings={})
        result=workflow_coordinator.ExplicitCoordinator._author_diagnostics(coordinator,{'run_id':self.run,'current_attempt':1,'input_digest':self.digest})
        self.assertEqual(result['skipped_checks'],8)
        self.assertLess(len(__import__('json').dumps(result,ensure_ascii=False).encode('utf-8')),48*1024)
        self.assertNotIn('\x01',__import__('json').dumps(result))


if __name__=='__main__': unittest.main()
