import tempfile
import unittest
import json
from pathlib import Path

import workflow_dependency
import workflow_store
import test_workflow_review_results as fixtures


class DependencyPauseTests(unittest.TestCase):
    def test_pause_persists_and_explicit_resume_preserves_active_deadline(self):
        helper=fixtures.ReviewResultTests(); helper.setUp()
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,_=helper.admitted(Path(tmp))
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            # Current assigned review remains pending while the provider circuit is open.
            deadline=task['deadline_ms']
            self.assertEqual('PAUSED',workflow_dependency.pause(store,task_id=task['task_id'],dependency_key='provider',failure_id='failure-1',binding=binding,now_ms=deadline-1000)['disposition'])
            reopened=workflow_store.WorkflowStore(store.path)
            self.assertTrue(reopened.task_is_dependency_paused(task['task_id']))
            self.assertEqual(None,api.aggregate(run,now_ms=deadline+10**9)['decision'])
            self.assertEqual('RESUMED',workflow_dependency.resume(reopened,task_id=task['task_id'],dependency_key='provider',binding=binding,now_ms=deadline+10**9)['disposition'])
            self.assertEqual('ERROR',api.aggregate(run,now_ms=deadline+10**9+1001)['decision'])

    def test_failure_id_deduplicates(self):
        helper=fixtures.ReviewResultTests(); helper.setUp()
        with tempfile.TemporaryDirectory() as tmp:
            store,_,task,_,_=helper.admitted(Path(tmp))
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            kwargs=dict(task_id=task['task_id'],dependency_key='provider',failure_id='same',binding=binding,now_ms=10)
            self.assertEqual('PAUSED',workflow_dependency.pause(store,**kwargs)['disposition'])
            self.assertEqual('DUPLICATE',workflow_dependency.pause(store,**kwargs)['disposition'])

    def test_resume_is_idempotent_and_second_failure_accumulates(self):
        helper=fixtures.ReviewResultTests(); helper.setUp()
        with tempfile.TemporaryDirectory() as tmp:
            store,run,task,api,_=helper.admitted(Path(tmp))
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            for fid,start,end in (('one',100,200),('two',300,450)):
                workflow_dependency.pause(store,task_id=task['task_id'],dependency_key='provider',failure_id=fid,binding=binding,now_ms=start)
                result=workflow_dependency.resume(store,task_id=task['task_id'],dependency_key='provider',binding=binding,now_ms=end)
                self.assertEqual((end-start) if fid=='one' else 250,result['paused_ms'])
                self.assertEqual('REPLAYED',workflow_dependency.resume(store,task_id=task['task_id'],dependency_key='provider',binding=binding,now_ms=end+900)['disposition'])
            self.assertEqual(250,store.get_task_dependency_pause(task['task_id'])['accumulated_paused_ms'])
            self.assertFalse(workflow_dependency.task_deadline_due(task,task['deadline_ms']+249,paused_ms=250))
            self.assertTrue(workflow_dependency.task_deadline_due(task,task['deadline_ms']+250,paused_ms=250))

    def test_conflicting_dependency_and_cancel_requested_resume_rejected(self):
        helper=fixtures.ReviewResultTests(); helper.setUp()
        with tempfile.TemporaryDirectory() as tmp:
            store,_,task,_,_=helper.admitted(Path(tmp))
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            workflow_dependency.pause(store,task_id=task['task_id'],dependency_key='provider',failure_id='one',binding=binding,now_ms=10)
            with self.assertRaises(workflow_store.StaleCommand):
                workflow_dependency.pause(store,task_id=task['task_id'],dependency_key='other',failure_id='two',binding=binding,now_ms=20)
            db=store._connect()
            try:
                saved=json.loads(db.execute('SELECT task_json FROM tasks WHERE id=?',(task['task_id'],)).fetchone()[0])
                saved['state']='CANCEL_REQUESTED'
                db.execute('UPDATE tasks SET task_json=? WHERE id=?',(json.dumps(saved),task['task_id']))
            finally: db.close()
            with self.assertRaises(workflow_store.StaleCommand):
                workflow_dependency.resume(store,task_id=task['task_id'],dependency_key='provider',binding=binding,now_ms=30)


if __name__=='__main__': unittest.main()
